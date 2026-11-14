import { cardEmulation, nfcController } from '@kit.ConnectivityKit';
import { bundleManager, common, Want } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { HceManager } from '../hce/HceManager';
import { SimulatorStore } from '../data/SimulatorStore';

/** 封装系统 NFC 路由；页面和脚本引擎不直接访问系统服务。 */
export class NfcController {
  private readonly store: SimulatorStore;
  private readonly context: common.UIAbilityContext;

  constructor(store: SimulatorStore, context: common.UIAbilityContext) {
    this.store = store;
    this.context = context;
  }

  /** 查询设备是否声明 NFC 能力；普通 NFC 硬件支持不等于支持 HCE。 */
  private supportsNfc(): boolean {
    try {
      return canIUse('SystemCapability.Communication.NFC.Core') && nfcController.isNfcSupported();
    } catch (error) {
      return false;
    }
  }

  /** 查询设备是否声明 HCE（Host Card Emulation，主机卡模拟）能力。 */
  private supportsHce(): boolean {
    try {
      return cardEmulation.hasHceCapability();
    } catch (error) {
      return false;
    }
  }

  private nfcOpen(): boolean {
    try {
      return nfcController.isNfcOpen();
    } catch (error) {
      return false;
    }
  }

  /** 查询本应用是否为系统 payment 类别的默认服务；本项目启动前要求满足此条件。 */
  private isDefault(): boolean {
    const element: bundleManager.ElementName | null = HceManager.getInstance().getElementName();
    if (element === null) {
      return false;
    }
    try {
      return cardEmulation.isDefaultService(element, cardEmulation.CardType.PAYMENT);
    } catch (error) {
      return false;
    }
  }

  /** 按硬件能力、NFC 开关、默认支付服务顺序生成状态文字；系统调用异常转为可显示的错误。 */
  status(): string {
    try {
      if (!this.supportsNfc() || !this.supportsHce()) {
        return '设备不支持 NFC HCE · 可调试脚本';
      }
      if (!this.nfcOpen()) {
        return 'NFC 未开启';
      }
      if (!this.isDefault()) {
        return '请设为默认支付应用';
      }
      return 'NFC HCE 就绪 · 保持屏幕解锁';
    } catch (error) {
      return `NFC 系统服务不可用：${(error as Error).message}`;
    }
  }

  /** 状态是否满足实际模拟条件；用于界面把状态文字标为可用或警示色。 */
  isReady(): boolean {
    return this.supportsNfc() && this.supportsHce() && this.nfcOpen() && this.isDefault();
  }

  /**
   * 幂等启动：已有实际启用状态就返回，否则检查设备条件并注册 PPSE 与当前卡的 AID。
   * AID（Application Identifier，应用标识符）用于系统选择路由。
   * 路由注册成功后才设置 enabled；注册路由不会替脚本生成 SELECT 回复。
   */
  start(): void {
    if (this.store.enabled) {
      return;
    }
    const card = this.store.selected();
    if (card === null) {
      throw new Error('请先选择卡片');
    }
    if (!this.supportsNfc()) {
      throw new Error('当前设备没有 NFC');
    }
    if (!this.supportsHce()) {
      throw new Error('设备不支持 HCE');
    }
    if (!this.nfcOpen()) {
      throw new Error('请先打开 NFC');
    }
    if (!this.isDefault()) {
      throw new Error('请到设备设置中将本应用设为默认支付应用');
    }
    // 注册成功后才启动模拟；失败不能伪装为已启动。
    HceManager.getInstance().ensureRoutes();
    this.store.setEnabled(true);
  }

  /** 仅在已启用时关闭应用模拟开关，让旧引擎修订号失效；不撤销系统的默认支付选择。 */
  stop(): void {
    if (this.store.enabled) {
      this.store.setEnabled(false);
    }
  }

  /** 打开系统设置的 NFC 页面；离开应用前台会由页面生命周期暂停模拟。 */
  nfcSettings(onError: (message: string) => void): void {
    this.launchNfcSettings(onError);
  }

  /** NFC 设置页内含“默认付款应用”入口，可在此完成默认支付应用切换。 */
  paymentSettings(onError: (message: string) => void): void {
    this.launchNfcSettings(onError);
  }

  /** 真机验证 uri 为 nfc_settings 时可直达 NFC 设置页；直达失败则回退设置主页。 */
  private launchNfcSettings(onError: (message: string) => void): void {
    const want: Want = {
      bundleName: 'com.huawei.hmos.settings',
      abilityName: 'com.huawei.hmos.settings.MainAbility',
      uri: 'nfc_settings'
    };
    this.context.startAbility(want).catch((error: BusinessError): void => {
      this.context.startAbility({
        bundleName: 'com.huawei.hmos.settings',
        abilityName: 'com.huawei.hmos.settings.MainAbility'
      }).catch((fallbackError: BusinessError): void => {
        onError(`无法打开系统设置：${fallbackError.message}`);
      });
    });
  }
}
