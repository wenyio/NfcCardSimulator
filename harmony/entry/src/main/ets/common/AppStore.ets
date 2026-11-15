import { bundleManager, common } from '@kit.AbilityKit';
import { CardPacks } from '../core/CardPack';
import { CardScript } from '../core/CardScript';
import { SimulatorStore } from '../data/SimulatorStore';
import { HceManager } from '../hce/HceManager';
import { NfcController } from '../nfc/NfcController';
import { DetailSimulationSession } from '../nfc/DetailSimulationSession';
import { EditorState } from './EditorState';

/**
 * 进程级应用状态入口，持有唯一仓库、NFC 控制器、详情会话与编辑草稿。
 * 页面实例重建时仍共享同一进程内数据；编辑草稿仅保存在进程内存。
 */
export class AppStore {
  private static instance: AppStore | null = null;
  readonly store: SimulatorStore;
  readonly nfc: NfcController;
  readonly session: DetailSimulationSession;
  // 进程内存编辑草稿；进程死亡后不恢复编辑页。
  editor: EditorState | null = null;
  // 编辑页是否从详情页打开；保存成功后需要回到详情并恢复开关意图。
  editorFromDetail: boolean = false;
  // 编辑页是否在导航栈上；期间详情会话保持 editing 状态。
  editorPending: boolean = false;
  // 打开编辑页时捕获的详情模拟开关意图；保存成功后恢复。
  editorRequested: boolean = true;
  private abilityForeground: boolean = true;
  private detailVisible: boolean = false;

  private constructor(context: common.UIAbilityContext) {
    this.store = new SimulatorStore(context);
    this.nfc = new NfcController(this.store, context);
    this.session = new DetailSimulationSession(
      (id: string): void => {
        this.store.select(id);
      },
      (): void => {
        this.nfc.start();
      },
      (): void => {
        this.nfc.stop();
      }
    );
  }

  /** EntryAbility onCreate 时调用；进程内只初始化一次。 */
  static initialize(context: common.UIAbilityContext,
    elementName: bundleManager.ElementName): void {
    if (AppStore.instance === null) {
      AppStore.instance = new AppStore(context);
    }
    // Ability 重建时也更新 HCE 绑定，进程单例可能仍然存活。
    HceManager.getInstance().attach(AppStore.instance.store, elementName);
  }

  static getInstance(): AppStore {
    if (AppStore.instance === null) {
      throw new Error('应用尚未完成初始化');
    }
    return AppStore.instance;
  }

  /** 前后台切换只暂停实际模拟，不清除用户的启动意图。 */
  setAbilityForeground(value: boolean): void {
    this.abilityForeground = value;
    this.session.foreground(this.abilityForeground && this.detailVisible);
  }

  /** 详情页可见状态变化；与前后台状态共同决定详情会话的前台条件。 */
  setDetailVisible(value: boolean): void {
    this.detailVisible = value;
    this.session.foreground(this.abilityForeground && this.detailVisible);
  }

  /**
   * 进入独立编辑页前建立草稿并暂停详情模拟：编辑内置卡强制转为复制，
   * 新建/副本落到当前列表包（内置包时回个人包），编辑保留原身份和归属。
   */
  openEditor(card: CardScript | null, copy: boolean, fromDetail: boolean): void {
    const current = this.store.currentPack();
    const target = current.builtIn ? CardPacks.PERSONAL : current.id;
    this.editor = EditorState.create(card, copy || (card !== null && card.builtIn), target);
    this.editorFromDetail = fromDetail;
    this.editorPending = true;
    this.editorRequested = this.session.isRequested;
    this.session.editing(true);
  }

  /** 编辑页离开（保存或放弃）后解除编辑占用；是否实际启动仍由详情会话条件决定。 */
  closeEditor(): void {
    this.editorPending = false;
    this.session.editing(false);
  }
}
