/**
 * 详情范围的模拟控制；页面重绘和工具切换不能重选卡或重置正在执行的步骤。
 * requested 是用户意图，foreground/editing 是页面条件；实际 enabled 状态由外部 NfcController 管理。
 * 这里只协调传入回调，没有射频硬件或脚本游标，不把三种状态混在一个开关里。
 */
export class DetailSimulationSession {
  private readonly select: (id: string) => void;
  private readonly start: () => void;
  private readonly stop: () => void;
  cardId: string | null = null;
  error: string | null = null;
  private requested: boolean = true;
  private inForeground: boolean = false;
  private isEditing: boolean = false;

  constructor(select: (id: string) => void, start: () => void, stop: () => void) {
    this.select = select;
    this.start = start;
    this.stop = stop;
  }

  get isRequested(): boolean {
    return this.requested;
  }

  /** 打开详情时仅在目标卡改变时停止并重选；同卡不重选，随后恢复默认启动意图并同步条件。 */
  open(id: string): void {
    if (this.cardId !== id) {
      this.stop();
      this.select(id);
    }
    this.cardId = id;
    this.requested = true;
    this.error = null;
    this.sync();
  }

  /** 清除详情目标和错误，恢复下次进入的默认意图，并停止实际模拟。 */
  leave(): void {
    this.cardId = null;
    this.error = null;
    this.requested = true;
    this.stop();
  }

  /** 更新页面是否允许前台模拟，然后同步；切后台只暂停实际状态，不清除启动意图。 */
  foreground(value: boolean): void {
    this.inForeground = value;
    this.sync();
  }

  /** 标记独立编辑页是否占用详情会话；编辑期间禁止启动，返回后重新检查条件。 */
  editing(value: boolean): void {
    this.isEditing = value;
    this.sync();
  }

  /** 记录用户开关意图；关闭时清理错误，再按前台和编辑状态决定是否实际启用。 */
  setRequested(value: boolean): void {
    this.requested = value;
    if (!value) {
      this.error = null;
    }
    this.sync();
  }

  /**
   * 集中计算是否允许启动：有卡、详情前台、未编辑且用户请求启动，四者必须同时满足。
   * 条件不满足就停止；启动失败保存错误供界面显示。start 回调必须幂等，避免重绘推进或重置会话。
   */
  private sync(): void {
    if (this.cardId === null || !this.inForeground || this.isEditing || !this.requested) {
      this.stop();
      return;
    }
    // start 必须幂等，返回设置或重绘页面不会重置已经启动的射频会话。
    try {
      this.start();
      this.error = null;
    } catch (error) {
      this.error = (error as Error).message;
    }
  }
}
