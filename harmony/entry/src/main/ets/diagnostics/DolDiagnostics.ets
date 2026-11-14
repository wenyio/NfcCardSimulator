import { ApduRule } from '../core/CardScript';
import { Hex } from '../core/Hex';
import { BerTlv, TlvValue } from './BerTlv';

/** DOL 中的一项：标签标识字段，length 是随后命令中该字段值的字节数。 */
export class DolField {
  readonly tag: string;
  readonly length: number;

  constructor(tag: string, length: number) {
    this.tag = tag;
    this.length = length;
  }
}

/**
 * 每个脚本会话独享定义与 GAC 次数；仅用已经成功匹配并回复的数据解释输入。
 * PDOL（Processing Options Data Object List，处理选项数据对象列表，标签 9F38）解释 GPO 输入。
 * CDOL（Card Risk Management Data Object List，卡片风险管理数据对象列表）解释 GENERATE AC 输入。
 * GPO（Get Processing Options，获取处理选项）；GAC 是此项目对 GENERATE AC（生成应用密文）的简称。
 * 定义只用于诊断；即使输入可解释，也不会重算 AC（Application Cryptogram，应用密文）。
 */
export class DolDiagnostics {
  private definitions: Map<string, string> = new Map<string, string>();
  private conflicts: Set<string> = new Set<string>();
  private gacCount: number = 0;
  // 字段名称仅用于诊断显示。完整英文释义和长度示例见 docs/NFC-GLOSSARY.md。
  // TTQ（Terminal Transaction Qualifiers，终端交易属性）、TVR（Terminal Verification Results，终端验证结果）。
  // ICC（Integrated Circuit Card，集成电路卡）；9F4C 是 ICC Dynamic Number（ICC 动态数）。
  private static names: Record<string, string> = {
    '9F02': '授权金额', '9F03': '其他金额', '9F1A': '终端国家代码',
    '5F2A': '交易货币代码', '9A': '交易日期', '9C': '交易类型', '9F37': '不可预知数（终端随机数）',
    '9F66': '终端交易属性 TTQ', '9F35': '终端类型', '9F33': '终端能力', '95': '终端验证结果 TVR',
    '9F34': '持卡人验证结果', '9F21': '交易时间', '9F45': '数据认证码', '9F4C': 'ICC 动态数'
  };

  /** 清除已观察到的 DOL 定义、冲突标记和成功 GAC 次数，避免跨应用沿用字段布局。 */
  reset(): void {
    this.definitions.clear();
    this.conflicts.clear();
    this.gacCount = 0;
  }

  /** 遇到 SELECT 命令头即清除诊断状态，即使后续未匹配；不重置脚本引擎的顺序游标。 */
  beforeCommand(command: string): void {
    if (command.startsWith('00A4')) {
      this.reset();
    }
  }

  /**
   * 只从成功命中规则所返回的 9000 响应学习定义，统计成功 GAC，并拒绝冲突的字段布局。
   * SELECT 响应提取 PDOL，READ RECORD 响应提取 CDOL1/2；不借用场景元数据猜测定义。
   */
  observe(command: string, response: string): void {
    // 9000 是成功状态字；错误响应、未命中的兜底响应均不用于学习定义。
    if (!response.endsWith('9000')) {
      return;
    }
    if (command.startsWith('80AE')) {
      this.gacCount++;
      return;
    }
    if (!command.startsWith('00A4') && !command.startsWith('00B2')) {
      return;
    }
    // 状态字占 2 字节，即 4 个十六进制字符，它不是 TLV 内容的一部分。
    let values: TlvValue[];
    try {
      const bodyEnd = Math.max(0, response.length - 4);
      values = BerTlv.parseHex(response.substring(0, bodyEnd));
    } catch (error) {
      return;
    }
    const keys: string[] = command.startsWith('00A4') ? ['9F38'] : ['8C', '8D'];
    for (const key of keys) {
      const entries = values.filter((item: TlvValue): boolean => item.tag === key);
      if (entries.length === 0) {
        continue;
      }
      const choices: string[] = [];
      for (const entry of entries) {
        if (!choices.includes(entry.value)) {
          choices.push(entry.value);
        }
      }
      const old = this.definitions.get(key);
      // 同标签定义不唯一时停止使用，后续同应用即使再收到单一值也不能消除歧义。
      if (choices.length !== 1 || (old !== undefined && old !== choices[0])) {
        this.definitions.delete(key);
        this.conflicts.add(key);
      } else if (!this.conflicts.has(key)) {
        this.definitions.set(key, choices[0]);
      }
    }
  }

  /**
   * 按本会话已知 PDOL/CDOL 拆解预期与实际命令的数据，生成差异说明。
   * 诊断不足或格式损坏时返回说明文字，不改规则、回复或顺序游标；matched 只控制提示语。
   */
  describe(rule: ApduRule, actual: string, matched: boolean): string {
    let type = '';
    if (actual.startsWith('80A8')) {
      type = '9F38';
    } else if (actual.startsWith('80AE') && this.gacCount < 2) {
      type = this.gacCount === 0 ? '8C' : '8D';
    } else {
      return '';
    }
    const title = type === '9F38' ? 'PDOL' : (type === '8C' ? 'CDOL1' : 'CDOL2');
    if (rule.command === actual) {
      return '';
    }
    if (this.conflicts.has(type)) {
      return `${title} 定义存在冲突，未按字段解释；请核对当前应用的回复。`;
    }
    const definition = this.definitions.get(type);
    if (definition === undefined) {
      return `${title}：当前会话尚未获得 ${type} 定义，仅保留原始命令差异。`;
    }
    try {
      const fields = this.parseDol(definition);
      const expected = this.commandData(rule.command, type === '9F38');
      const input = this.commandData(actual, type === '9F38');
      let total = 0;
      for (const field of fields) {
        total += field.length;
      }
      const size = total * 2;
      if (expected.length !== size || input.length !== size) {
        throw new Error(`DOL 要求 ${size / 2} 字节，期望输入 ${expected.length / 2}，实际输入 ${input.length / 2}`);
      }
      // 此处对字符串切片，所以 DOL 的字节数必须乘以 2 才是字符偏移。
      let offset = 0;
      const changes: string[] = [];
      for (const field of fields) {
        const end = offset + field.length * 2;
        const expectedValue = expected.substring(offset, end);
        const actualValue = input.substring(offset, end);
        offset = end;
        if (expectedValue !== actualValue) {
          changes.push(`${field.tag} ${DolDiagnostics.names[field.tag] ?? '未知字段'}：期望 ${expectedValue} / 实际 ${actualValue}`);
        }
      }
      if (changes.length === 0) {
        return `${title} 数据一致；差异位于 APDU 头、长度或 Le。`;
      }
      const head = matched ? `规则已匹配，但固定回复的 ${title} 输入不同（不会重算签名或密文）：\n`
        : `${title} 字段差异：\n`;
      const shown = changes.slice(0, 20).join('\n');
      const tail = changes.length > 20 ? '\n其余差异省略' : '';
      return head + shown + tail;
    } catch (error) {
      const err = error as Error;
      return `${title} 无法按字段解释：${err.message}`;
    }
  }

  /**
   * 把 DOL 定义拆成标签与长度列表；每项没有值，长度是随后命令应提供的字节数。
   * DOL（Data Object List，数据对象列表）与 TLV 不同，不能把长度后的内容当成该字段值。
   */
  private parseDol(value: string): DolField[] {
    if (!Hex.valid(value) || value.length > 8192) {
      throw new Error('DOL 定义格式错误');
    }
    const bytes = Hex.bytes(value);
    let offset = 0;
    const fields: DolField[] = [];
    while (offset < bytes.length) {
      const start = offset;
      const first = bytes[offset] & 255;
      offset++;
      // 标签延续规则与 BER-TLV 一致，但标签之后的长度规则不同。
      if ((first & 31) === 31) {
        let next = 0;
        do {
          if (offset >= bytes.length || offset - start >= 4) {
            throw new Error('DOL Tag 截断');
          }
          next = bytes[offset] & 255;
          offset++;
        } while ((next & 128) !== 0);
      }
      const tag = Hex.text(bytes.slice(start, offset));
      if (offset >= bytes.length) {
        throw new Error('DOL 缺少字段长度');
      }
      // DOL 的长度是单字节数值，不使用 BER 长度编码。
      // 例如 9F02 06 9F37 04 表示后续数据先给 6 字节金额，再给 4 字节不可预知数。
      fields.push(new DolField(tag, bytes[offset] & 255));
      offset++;
    }
    return fields;
  }

  /**
   * 校验含数据的短/扩展命令 APDU 长度并提取数据域，允许带或不带 Le。
   * gpo 为 true 时还要求唯一 83 模板，返回其值；此函数只供 DOL 诊断，不是通用 APDU 解析器。
   */
  private commandData(command: string, gpo: boolean): string {
    if (!Hex.valid(command) || command.length < 10) {
      throw new Error('APDU 缺少 Lc');
    }
    // 命令头 CLA/INS/P1/P2 共 4 字节，即前 8 字符；其后读取 Lc（Length of Command Data，命令数据长度）。
    const shortLength = parseInt(command.substring(8, 10), 16);
    let start = 0;
    let length = 0;
    let le = 0;
    if (shortLength !== 0) {
      start = 10;
      length = shortLength * 2;
      le = 2;
    } else {
      if (command.length < 14) {
        throw new Error('扩展 APDU 缺少 Lc');
      }
      // 00 为扩展长度标志，接下来的两字节才是 Lc；此函数前提是含数据的 GPO/GAC。
      start = 14;
      length = parseInt(command.substring(10, 14), 16) * 2;
      le = 4;
    }
    // Le（Expected Response Data Length，期望响应数据长度）可选，短格式占 1 字节，扩展格式占 2 字节。
    // 这里只核对编码占用空间，不解码 Le=0 对应的最大期望长度。
    if (command.length !== start + length && command.length !== start + length + le) {
      throw new Error('Lc 与命令长度不一致');
    }
    const data = command.substring(start, start + length);
    if (!gpo) {
      return data;
    }
    // GPO 的数据先包在标签 83 中；83 的值才是依 PDOL 排列的字段，不是嵌套 TLV。
    const values = BerTlv.parseHex(data);
    if (values.length !== 1 || values[0].tag !== '83') {
      throw new Error('GPO 缺少唯一 83 数据模板');
    }
    return values[0].value;
  }
}
