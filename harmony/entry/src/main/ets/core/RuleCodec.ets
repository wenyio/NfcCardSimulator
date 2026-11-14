import { ApduRule, MatchMode } from './CardScript';
import { Json } from './Json';

interface RawRuleJson {
  command: string;
  response: string;
  match: string;
  label: string;
  mask?: string;
}

/** 只转换字段；草稿允许未完成输入，导入校验仍由 ScriptCodec 负责。 */
export class RuleCodec {
  /** 编码一条规则；正式导出只为 MASK 模式输出掩码，草稿则保留未完成的掩码输入。 */
  static encode(rule: ApduRule, draft: boolean = false): Object {
    const raw: RawRuleJson = {
      command: rule.command,
      response: rule.response,
      match: rule.mode as string,
      label: rule.label
    };
    if (draft || rule.mode === MatchMode.MASK) {
      raw.mask = rule.mask;
    }
    return raw;
  }

  /** 读取必需的 match 枚举名；未识别的取值直接抛异常。 */
  static modeOf(record: Record<string, Object>): MatchMode {
    const text = Json.str(record['match'], 'EXACT').toUpperCase();
    if (text === 'EXACT') {
      return MatchMode.EXACT;
    }
    if (text === 'MASK') {
      return MatchMode.MASK;
    }
    if (text === 'PREFIX') {
      return MatchMode.PREFIX;
    }
    throw new Error('match 须为 EXACT、MASK 或 PREFIX');
  }

  /** 按当前字段名读取规则，不执行十六进制或长度校验，允许编辑草稿包含未完成输入。 */
  static decode(record: Record<string, Object>, mode: MatchMode): ApduRule {
    return new ApduRule(Json.str(record['command'], ''), Json.str(record['response'], ''),
      mode, Json.str(record['mask'], ''), Json.str(record['label'], ''));
  }
}
