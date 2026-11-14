import { hilog } from '@kit.PerformanceAnalysisKit';
import { Files } from '../common/Files';
import { Json } from '../core/Json';

/** time 为 Unix 毫秒时间；kind 为方向或事件，session 供分组，cardId 保存组合 key 供按卡筛选。 */
export class LogEntry {
  readonly time: number;
  readonly kind: string;
  readonly message: string;
  readonly session: string;
  readonly cardId: string;

  constructor(time: number, kind: string, message: string, session: string = '系统', cardId: string = '') {
    this.time = time;
    this.kind = kind;
    this.message = message;
    this.session = session;
    this.cardId = cardId;
  }
}

interface RawLogEntry {
  time: number;
  kind: string;
  message: string;
  session: string;
  cardId: string;
}

/** 有界日志仓库；射频回调只追加内存，300 毫秒延迟合并写盘，避免每条 APDU 同步写文件。 */
export class LogRepository {
  private readonly path: string;
  private entries: LogEntry[] = [];
  private timerId: number = -1;

  constructor(path: string) {
    this.path = path;
    this.load();
  }

  private load(): void {
    try {
      if (!Files.exists(this.path)) {
        return;
      }
      const text = Files.readText(this.path);
      const array = Json.asArray(Json.parse(text));
      if (array === null) {
        throw new Error('日志格式错误');
      }
      for (const item of array) {
        const record = Json.asRecord(item);
        if (record === null) {
          continue;
        }
        this.entries.push(new LogEntry(Json.num(record['time'], 0), Json.str(record['kind'], ''),
          Json.str(record['message'], ''), Json.str(record['session'], '系统'),
          Json.str(record['cardId'], '')));
      }
      this.trim();
    } catch (error) {
      this.entries = [new LogEntry(Date.now(), '错误', `历史日志读取失败：${(error as Error).message}`)];
    }
  }

  /** 移除最早日志，直到满足最多 600 条及消息总字符数上限。 */
  private trim(): void {
    let total = 0;
    for (const entry of this.entries) {
      total += entry.message.length;
    }
    while (this.entries.length > 600 || total > 1000000) {
      const removed = this.entries.shift();
      if (removed === undefined) {
        break;
      }
      total -= removed.message.length;
    }
  }

  /** 截断过长消息、追加日志、裁剪总量并安排异步写盘。 */
  append(entry: LogEntry): void {
    const message = entry.message.length > 16000 ?
      `${entry.message.substring(0, 16000)} …[长记录截断]` : entry.message;
    this.entries.push(new LogEntry(entry.time, entry.kind, message, entry.session, entry.cardId));
    this.trim();
    this.schedule();
  }

  /** 复制当前日志列表，外部遍历不会直接持有可变队列。 */
  snapshot(): LogEntry[] {
    return this.entries.slice();
  }

  /** 清全部或指定卡片日志，并安排保存；cardId 为 null 表示全部。 */
  clear(cardId: string | null): void {
    if (cardId === null) {
      this.entries = [];
    } else {
      this.entries = this.entries.filter((entry: LogEntry): boolean => entry.cardId !== cardId);
    }
    this.schedule();
  }

  /** 合并写入请求：尚无待执行任务时安排 300 毫秒后写盘，不为每条记录重新计时。 */
  private schedule(): void {
    if (this.timerId === -1) {
      this.timerId = setTimeout(() => {
        this.timerId = -1;
        this.persist();
      }, 300);
    }
  }

  /** 在写盘期间新 APDU 仍可追加日志；失败记入系统日志。 */
  private persist(): void {
    try {
      const raw: RawLogEntry[] = [];
      for (const entry of this.entries) {
        raw.push({
          time: entry.time,
          kind: entry.kind,
          message: entry.message,
          session: entry.session,
          cardId: entry.cardId
        });
      }
      Files.writeTextAtomic(this.path, JSON.stringify(raw));
    } catch (error) {
      hilog.error(0x0000, 'SimulatorLogs', '日志保存失败：%{public}s', (error as Error).message);
    }
  }

  /** 提交一次写盘，是会阻塞的显式同步点；正常命令处理不调用。 */
  flush(): void {
    if (this.timerId !== -1) {
      clearTimeout(this.timerId);
      this.timerId = -1;
    }
    this.persist();
  }
}
