import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { util } from '@kit.ArkTS';
import { CardPack, CardPacks } from '../core/CardPack';
import { CardScript } from '../core/CardScript';
import { CardSchemeStore } from './CardSchemeStore';
import { CardPackRepository } from './CardPackRepository';
import { LogEntry, LogRepository } from './LogRepository';

/**
 * 进程共享的应用状态与业务入口，协调卡包仓库、选卡、实际启用状态和日志。
 * 变化通知直接分发给已注册监听；界面不直接操作存储文件。
 */
export class SimulatorStore {
  private readonly store: preferences.Preferences;
  private readonly repository: CardPackRepository;
  private readonly history: LogRepository;
  private readonly listeners: Set<() => void> = new Set<() => void>();
  activePackId: string;
  selectedId: string | null = null;
  enabled: boolean = false;
  // 选卡、启停或修改使此值增加；服务与调试页在下一条命令前据此识别旧引擎。
  sessionRevision: number = 0;
  readonly schemes: CardSchemeStore;

  constructor(context: common.Context) {
    this.store = preferences.getPreferencesSync(context, { name: 'simulator' });
    this.repository = new CardPackRepository(context);
    const activePack = this.store.getSync('activePack', CardPacks.DEMO);
    this.activePackId = typeof activePack === 'string' ? activePack : CardPacks.DEMO;
    const selected = this.store.getSync('selected', '');
    this.selectedId = typeof selected === 'string' && selected.length > 0 ? selected : null;
    this.history = new LogRepository(`${context.filesDir}/history.json`);
    this.schemes = new CardSchemeStore(context);
    this.schemes.setCardsProvider((): CardScript[] => this.cards());
    // 模拟状态属于当前详情会话，进程重启不能恢复上次的启动状态。
    this.store.deleteSync('enabled');
    this.store.flushSync();
    if (this.repository.readError.length > 0) {
      this.log('错误', this.repository.readError);
    }
    if (this.selected() === null) {
      this.enabled = false;
    }
  }

  /** 取得所有卡包，包含内置只读包和用户包。 */
  packs(): CardPack[] {
    return this.repository.packs();
  }

  /** 取得当前列表使用的包；当前 ID 无效时显示内置演示包。 */
  currentPack(): CardPack {
    const found = this.repository.find(this.activePackId);
    if (found !== null) {
      return found;
    }
    const demo = this.repository.find(CardPacks.DEMO);
    if (demo !== null) {
      return demo;
    }
    throw new Error('内置演示卡包缺失');
  }

  /** 取得当前列表包中的卡片；与正在选择的模拟卡状态分别维护。 */
  currentCards(): CardScript[] {
    return this.currentPack().cards;
  }

  /** 取得所有包的卡片，供查找、编辑和组织选项使用。 */
  cards(): CardScript[] {
    return this.repository.cards();
  }

  /** 优先按包与卡片组合 key 查找；裸 ID 仅在全局唯一时返回，歧义时返回 null。 */
  findCard(key: string): CardScript | null {
    const all = this.cards();
    for (const card of all) {
      if (card.key === key) {
        return card;
      }
    }
    const matches = all.filter((card: CardScript): boolean => card.id === key);
    return matches.length === 1 ? matches[0] : null;
  }

  /** 按 selectedId 解析当前选中卡，尚未选择或卡已不存在时返回 null。 */
  selected(): CardScript | null {
    const id = this.selectedId;
    return id === null ? null : this.findCard(id);
  }

  /** 选择卡片并停止模拟，递增会话修订号，使后续收发重建引擎。 */
  select(id: string): void {
    const card = this.findCard(id);
    if (card === null) {
      throw new Error('卡片不存在或编号不唯一');
    }
    this.selectedId = card.key;
    this.enabled = false;
    this.sessionRevision++;
    this.store.putSync('selected', card.key);
    this.store.deleteSync('enabled');
    this.store.flushSync();
    this.log('卡片', `已选择 ${card.name}`);
  }

  /**
   * 验证目标包后停止模拟、清除选卡并保存列表包 ID；切包本身不自动选择卡片。
   * 先更新全部状态再调用 setEnabled：其日志会同步 notifyChanged，监听者必须读到新卡包，
   * 否则末尾不再有通知，Index 的 @State 快照停留在旧包（对齐 Android 主线程延迟通知语义）。
   */
  switchPack(id: string): void {
    if (this.repository.find(id) === null) {
      throw new Error('卡包不存在');
    }
    this.activePackId = id;
    this.selectedId = null;
    this.store.putSync('activePack', id);
    this.store.deleteSync('selected');
    this.store.flushSync();
    this.setEnabled(false);
  }

  /** 更新实际模拟开关并递增修订号；启用前必须存在选中卡。 */
  setEnabled(value: boolean): void {
    if (value && this.selected() === null) {
      throw new Error('请先选择卡片');
    }
    this.enabled = value;
    this.sessionRevision++;
    this.log('状态', value ? '模拟已启动，请保持手机解锁' : '模拟已停止');
  }

  /** 提交卡包，成功后切换列表到目标包并停止模拟；失败不执行后续切包。 */
  importPack(pack: CardPack, targetId: string | null): CardPack {
    const saved = this.repository.importPack(pack, targetId);
    this.switchPack(saved.id);
    return saved;
  }

  /** 委托仓库重命名；涉及写盘，界面放到后台执行的逻辑与此方法共用同一出口。 */
  renamePack(id: string, name: string): void {
    this.repository.rename(id, name);
  }

  /** 创建用户包，保存成功后切换到新包，返回新包。 */
  createPack(name: string, description: string): CardPack {
    const pack = this.repository.createPack(name, description);
    this.switchPack(pack.id);
    return pack;
  }

  /** 保存编辑或移动结果，必要时更新选卡 key，再停止模拟让旧脚本会话失效。 */
  saveEditedCard(card: CardScript, originalKey: string | null): void {
    this.repository.saveEditedCard(card, originalKey);
    if (originalKey !== null && this.selectedId === originalKey) {
      this.selectedId = card.key;
      this.store.putSync('selected', card.key);
      this.store.flushSync();
    }
    this.setEnabled(false);
  }

  /** 删除用户包，清理失效的选卡并停止模拟；删除当前包时切回演示包。 */
  deletePack(id: string): void {
    this.repository.delete(id);
    const selected = this.selected();
    if (selected === null || selected.packId === id) {
      this.selectedId = null;
      this.store.deleteSync('selected');
      this.store.flushSync();
    }
    this.setEnabled(false);
    if (this.activePackId === id) {
      this.switchPack(CardPacks.DEMO);
    }
  }

  /** 删除指定用户卡，清理对应选卡并停止模拟；仓库拒绝删除内置卡。 */
  delete(id: string): void {
    const card = this.findCard(id);
    if (card === null) {
      throw new Error('卡片不存在');
    }
    this.repository.deleteCard(card.key);
    if (this.selectedId === card.key) {
      this.selectedId = null;
      this.store.deleteSync('selected');
      this.store.flushSync();
    }
    this.setEnabled(false);
  }

  /** 生成用于日志分组的会话标识并记录开始事件；此标识本身不建立射频连接。 */
  beginSession(kind: string, card: string): string {
    const id = `${kind}-${util.generateRandomUUID(false).substring(0, 8)}`;
    this.log('会话开始', card, id);
    return id;
  }

  /** 追加含时间、会话和当前选卡 key 的日志，再投递变化通知；日志写盘异步合并。 */
  log(kind: string, text: string, session: string = '系统'): void {
    this.history.append(new LogEntry(Date.now(), kind, text, session, this.selectedId ?? ''));
    this.notifyChanged();
  }

  /** 取得日志快照供展示或导出，不直接访问可变队列。 */
  logs(): LogEntry[] {
    return this.history.snapshot();
  }

  /** 清除全部或指定卡片日志后通知界面；null 表示全部。 */
  clearLogs(cardId: string | null): void {
    this.history.clear(cardId);
    this.notifyChanged();
  }

  /** 等待日志执行一次保存，用于显式同步；正常命令处理不调用。 */
  flushLogs(): void {
    this.history.flush();
  }

  /** 按卡片可选过滤日志，生成含毫秒时间、会话和方向的导出文本。 */
  logText(cardId: string | null): string {
    const lines: string[] = [];
    for (const entry of this.logs()) {
      if (cardId !== null && entry.cardId !== cardId) {
        continue;
      }
      lines.push(`${SimulatorStore.formatTime(entry.time)} [${entry.session}][${entry.kind}] ${entry.message}`);
    }
    return lines.join('\n');
  }

  /** 注册无参变化监听；页面离开时应对称移除。 */
  addListener(listener: () => void): void {
    this.listeners.add(listener);
  }

  /** 移除页面变化监听，避免失去前台后继续刷新视图。 */
  removeListener(listener: () => void): void {
    this.listeners.delete(listener);
  }

  private notifyChanged(): void {
    const snapshot: (() => void)[] = [];
    for (const listener of this.listeners) {
      snapshot.push(listener);
    }
    for (const listener of snapshot) {
      listener();
    }
  }

  private static pad(value: number, width: number = 2): string {
    let text = `${value}`;
    while (text.length < width) {
      text = `0${text}`;
    }
    return text;
  }

  private static formatTime(time: number): string {
    const date = new Date(time);
    return `${date.getFullYear()}-${SimulatorStore.pad(date.getMonth() + 1)}-${SimulatorStore.pad(date.getDate())} ` +
      `${SimulatorStore.pad(date.getHours())}:${SimulatorStore.pad(date.getMinutes())}:` +
      `${SimulatorStore.pad(date.getSeconds())}.${SimulatorStore.pad(date.getMilliseconds(), 3)}`;
  }
}
