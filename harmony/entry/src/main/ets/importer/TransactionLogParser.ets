import { util } from '@kit.ArkTS';
import { ApduRule, CardScript, MatchMode } from '../core/CardScript';
import { Hex } from '../core/Hex';
import { ScriptCodec } from '../core/ScriptCodec';
import { ScriptValidation } from '../core/ScriptValidation';

/** 从日志取得的一组完整收发；line 为原命令在源文件中的 1 起始行号，便于定位错误。 */
export class CapturedPair {
  readonly command: string;
  readonly response: string;
  readonly line: number;

  constructor(command: string, response: string, line: number) {
    this.command = command;
    this.response = response;
    this.line = line;
  }
}

/** 一笔捕获交易及配对问题；有问题可预览，但不能直接转换为可保存卡片。 */
export class CapturedTransaction {
  readonly pairs: CapturedPair[];
  readonly issues: string[];
  static readonly PPSE: string = '325041592E5359532E4444463031';

  constructor(pairs: CapturedPair[], issues: string[]) {
    this.pairs = pairs;
    this.issues = issues;
  }

  /** 从短格式 SELECT by name 命令提取 5～16 字节业务 AID 并去重，排除 PPSE 发现入口。 */
  aids(): string[] {
    const result: string[] = [];
    for (const pair of this.pairs) {
      const command = pair.command;
      if (!command.startsWith('00A40400') || command.length < 10) {
        continue;
      }
      const size = parseInt(command.substring(8, 10), 16);
      if (size < 5 || size > 16 || command.length < 10 + size * 2) {
        continue;
      }
      const aid = command.substring(10, 10 + size * 2);
      if (aid === CapturedTransaction.PPSE) {
        continue;
      }
      if (!result.includes(aid)) {
        result.push(aid);
      }
    }
    return result;
  }

  /** 把无错误的收发对转换为按步骤执行的固定回复卡片，再检查大小与内容；不推导动态算法。 */
  toCard(name: string, scheme: string, aids: string[]): CardScript {
    if (this.issues.length > 0) {
      throw new Error('日志有错误，不能导入');
    }
    const rules: ApduRule[] = [];
    for (const pair of this.pairs) {
      rules.push(new ApduRule(pair.command, pair.response, MatchMode.EXACT, '', `日志行 ${pair.line}`));
    }
    const card = new CardScript(`log-${util.generateRandomUUID(false)}`, name, scheme, aids,
      '日志固定回复；未计算动态密文或签名。', rules, true);
    ScriptCodec.checkSize(ScriptCodec.encode([card]));
    return ScriptValidation.card(card);
  }
}

/** 解析器的可变状态；一次只等待一个命令的回复。 */
class ParserState {
  result: CapturedTransaction[] = [];
  pairs: CapturedPair[] = [];
  issues: string[] = [];
  command: string | null = null;
  commandLine: number = 0;
  direction: string | null = null;
  buffer: string = '';
  startLine: number = 0;

  /**
   * 结算当前方向缓冲，完成命令/回复配对或记录问题；新 PPSE 选择可开启下一笔交易。
   * 无效回复也消耗待配对命令，避免后续回复被错配给上一条命令。
   */
  flush(): void {
    const dir = this.direction;
    if (dir === null) {
      return;
    }
    const value = Hex.clean(this.buffer);
    if (!Hex.valid(value) || value.length < (dir === 'T' ? 8 : 4)) {
      this.issues.push(`第 ${this.startLine} 行：${dir} 数据不是完整十六进制 APDU`);
      // 错误回复消耗当前命令，避免把下一条回复错误配对。
      if (this.command !== null) {
        this.issues.push(`第 ${this.commandLine} 行：命令未获得有效回复`);
      }
      this.command = null;
    } else if (dir === 'T') {
      if (this.command !== null) {
        this.issues.push(`第 ${this.commandLine} 行：命令缺少回复`);
      }
      // 只有明确 PPSE 选择才自动分组，其余重复命令保留执行顺序。
      if (value.startsWith(`00A404000E${CapturedTransaction.PPSE}`) && this.pairs.length > 0) {
        this.result.push(new CapturedTransaction(this.pairs.slice(), this.issues.slice()));
        this.pairs = [];
        this.issues = [];
      }
      this.command = value;
      this.commandLine = this.startLine;
    } else {
      const pending = this.command;
      if (pending === null) {
        this.issues.push(`第 ${this.startLine} 行：回复前没有命令`);
      } else {
        this.pairs.push(new CapturedPair(pending, value, this.commandLine));
        this.command = null;
      }
    }
    this.direction = null;
    this.buffer = '';
  }
}

/** 严格解析 T:/C:，不猜测方向、不补造缺失回复。 */
export class TransactionLogParser {
  /**
   * 严格读取 T: 终端命令与 C: 卡片回复，支持十六进制续行、注释和交易分隔。
   * 缺失、损坏或错序记录在 issues 中；不补造回复，文件过大或无记录时直接抛异常。
   */
  static parse(text: string): CapturedTransaction[] {
    if (ScriptCodec.byteLength(text) > ScriptCodec.MAX_BYTES) {
      throw new Error('日志超过 2 MB');
    }
    const state = new ParserState();
    const lines = text.replace(/^\uFEFF/, '').split(/\r\n|\r|\n/);
    for (let index = 0; index < lines.length; index++) {
      const line = lines[index].trim();
      const marker = /^([TC]):(.*)$/i.exec(line);
      if (marker !== null) {
        state.flush();
        state.direction = (marker[1] ?? '').toUpperCase();
        state.buffer = marker[2] ?? '';
        state.startLine = index + 1;
      } else if (line.length === 0 || line.startsWith('#')) {
        // 空行与 # 注释跳过。
      } else if (line === '---') {
        state.flush();
        if (state.command !== null) {
          state.issues.push(`第 ${state.commandLine} 行：命令缺少回复`);
          state.command = null;
        }
        if (state.pairs.length > 0 || state.issues.length > 0) {
          state.result.push(new CapturedTransaction(state.pairs.slice(), state.issues.slice()));
        }
        state.pairs = [];
        state.issues = [];
      } else if (state.direction !== null && Hex.valid(Hex.clean(line))) {
        state.buffer += line;
      } else {
        state.flush();
        state.issues.push(`第 ${index + 1} 行：无法识别，仅支持 T:/C:、十六进制续行、# 注释、--- 分隔`);
      }
    }
    state.flush();
    if (state.command !== null) {
      state.issues.push(`第 ${state.commandLine} 行：命令缺少回复`);
    }
    if (state.pairs.length > 0 || state.issues.length > 0) {
      state.result.push(new CapturedTransaction(state.pairs, state.issues));
    }
    if (state.result.length === 0) {
      throw new Error('未找到命令回复');
    }
    if (state.result.length > 200) {
      throw new Error('交易数超过 200');
    }
    return state.result;
  }
}
