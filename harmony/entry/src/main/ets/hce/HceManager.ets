import { cardEmulation } from '@kit.ConnectivityKit';
import { AsyncCallback, BusinessError } from '@kit.BasicServicesKit';
import { bundleManager } from '@kit.AbilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { Hex } from '../core/Hex';
import { ScriptEngine } from '../core/ScriptEngine';
import { SimulatorStore } from '../data/SimulatorStore';

const PPSE_AID = '325041592E5359532E4444463031';
const DOMAIN = 0x0000;
const TAG = 'HceManager';

/**
 * NFC（Near Field Communication，近场通信）的真实命令入口。
 * HCE（Host Card Emulation，主机卡模拟）让应用在设备主机处理卡片命令，射频传输由系统负责。
 * 服务声明和静态路由见 module.json5；动态路由由本类按选中卡注册。
 * 不负责读取实体卡或模拟卡片 UID（Unique Identifier，唯一标识符）；这里只处理被路由来的 APDU。
 */
export class HceManager {
  private static instance: HceManager | null = null;
  private readonly hceService: cardEmulation.HceService = new cardEmulation.HceService();
  private store: SimulatorStore | null = null;
  private elementName: bundleManager.ElementName | null = null;
  private cmdCallback: AsyncCallback<number[]> | null = null;
  private subscribed: boolean = false;
  private routed: boolean = false;
  private routedAids: string = '';
  // 记录引擎创建时的配置修订号；与仓库不同时，旧会话不能继续使用。
  private engine: ScriptEngine | null = null;
  private revision: number = -1;
  private session: string = 'NFC';

  static getInstance(): HceManager {
    if (HceManager.instance === null) {
      HceManager.instance = new HceManager();
    }
    return HceManager.instance;
  }

  getElementName(): bundleManager.ElementName | null {
    return this.elementName;
  }

  attach(store: SimulatorStore, elementName: bundleManager.ElementName): void {
    this.store = store;
    this.elementName = elementName;
    if (this.cmdCallback === null) {
      this.cmdCallback = (error: BusinessError, command: number[]): void => {
        this.handleCommand(error, command);
      };
    }
  }

  /**
   * 幂等注册 HCE 路由：AID 未变化时直接返回，变化时先停旧路由再注册新 AID。
   * 路由注册后保持 hceCmd 订阅；停止模拟只切换 store.enabled，禁用时仍回 6985 并记日志。
   */
  ensureRoutes(): void {
    const store = this.store;
    const element = this.elementName;
    const callback = this.cmdCallback;
    if (store === null || element === null || callback === null) {
      throw new Error('应用尚未完成初始化');
    }
    const card = store.selected();
    if (card === null) {
      throw new Error('请先选择卡片');
    }
    // PPSE（Proximity Payment System Environment，近距离支付系统环境）是常见非接触支付发现入口。
    // 此十六进制值对应 ASCII 文本 2PAY.SYS.DDF01；业务应用 AID 另由卡片配置提供。
    const aids: string[] = [PPSE_AID];
    for (const aid of card.aids) {
      if (!aids.includes(aid)) {
        aids.push(aid);
      }
    }
    const signature = aids.join(' ');
    if (this.routed && this.routedAids === signature) {
      return;
    }
    try {
      if (this.routed) {
        this.hceService.stop(element);
      }
      this.hceService.start(element, aids);
      if (!this.subscribed) {
        this.hceService.on('hceCmd', callback);
        this.subscribed = true;
      }
    } catch (error) {
      const err = error as BusinessError;
      this.routed = false;
      // 注册成功后才启动模拟；失败不能伪装为已启动。
      store.log('路由错误', `element=${element.abilityName}; category=payment; aids=${signature}; result=false`);
      throw new Error(`系统未接受 AID 路由注册，模拟未启动。请确认设备支持 HCE，详见日志。（${err.message}）`);
    }
    if (!this.routed) {
      store.log('会话', `已注册 AID 路由：${signature}`);
    }
    this.routed = true;
    this.routedAids = signature;
  }

  /** 仅应用销毁时注销路由和订阅；正常停止模拟不调用，不撤销系统的默认支付选择。 */
  destroy(): void {
    const element = this.elementName;
    if (this.routed && element !== null) {
      try {
        this.hceService.stop(element);
      } catch (error) {
        hilog.warn(DOMAIN, TAG, 'stop failed: %{public}s', JSON.stringify(error));
      }
      this.routed = false;
    }
    if (this.subscribed) {
      try {
        this.hceService.off('hceCmd');
      } catch (error) {
        hilog.warn(DOMAIN, TAG, 'off failed: %{public}s', JSON.stringify(error));
      }
      this.subscribed = false;
    }
  }

  /** 系统将路由到本服务的 C-APDU 交给此回调；执行规则匹配和内存日志追加，日志文件另异步保存。 */
  private handleCommand(error: BusinessError, command: number[]): void {
    const store = this.store;
    if (store === null) {
      return;
    }
    // 6700：长度错误。本分支防御空命令，正常系统回调应提供命令。
    if (error) {
      store.log('错误', `接收 APDU 失败：${error.message}`);
      return;
    }
    if (command === null || command.length === 0) {
      store.log('C → T', '6700 · 收到空命令');
      this.transmit('6700');
      return;
    }
    const input = Hex.text(command);
    const card = store.selected();
    if (!store.enabled || card === null) {
      this.engine = null;
      // 6985：使用条件不满足。路由仍可能存在，但应用当前没有允许脚本模拟。
      store.log('C → T', '6985 · 模拟未启动');
      this.transmit('6985');
      return;
    }
    // 射频会话固定脚本版本；重复选择 AID 不重置步骤，断场或配置变化才重建引擎。
    if (this.engine === null || this.engine.script !== card || this.revision !== store.sessionRevision) {
      this.engine = new ScriptEngine(card);
      this.revision = store.sessionRevision;
      this.session = store.beginSession('NFC', card.name);
    }
    // T（Terminal，终端）发命令给 C（Card，卡片）；回复方向相反。
    store.log('T → C', input, this.session);
    try {
      const result = this.engine.exchange(input);
      store.log('C → T', `${result.response} · ${result.rule}\n${result.difference}`, this.session);
      this.transmit(result.response);
    } catch (err) {
      store.log('错误', `响应失败：${(err as Error).message}`, this.session);
      // 6F00：无精确诊断的失败；此处作为匹配/响应转换异常时的服务兜底。
      this.transmit('6F00');
    }
  }

  private transmit(hex: string): void {
    try {
      this.hceService.transmit(Hex.bytes(hex)).catch((err: BusinessError): void => {
        hilog.error(DOMAIN, TAG, 'transmit failed: %{public}s', JSON.stringify(err));
      });
    } catch (error) {
      hilog.error(DOMAIN, TAG, 'transmit invalid: %{public}s', JSON.stringify(error));
    }
  }
}
