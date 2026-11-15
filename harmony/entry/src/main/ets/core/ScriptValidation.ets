import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { util } from '@kit.ArkTS';
import { ApduRule, CardScript, MatchMode } from './CardScript';
import { CardMetadata } from './CardMetadata';
import { Hex } from './Hex';
import { CardMetadataCodec } from './CardMetadataCodec';
import { ScriptCodec } from './ScriptCodec';

export class ScriptValidation {
  static hex(value: string, field: string, minBytes: number = 0): string {
    const result = Hex.clean(value);
    if (!Hex.valid(result) || result.length < minBytes * 2) {
      throw new Error(`${field}：十六进制长度或内容错误`);
    }
    if (result.length > 131080) {
      throw new Error(`${field} 过长`);
    }
    return result;
  }

  static rule(rule: ApduRule, index: number = 0): ApduRule {
    // PREFIX 可只比较 CLA/INS；真实输入仍由引擎检查完整命令头。
    const command = ScriptValidation.hex(rule.command, `规则 ${index + 1} command`, rule.mode === MatchMode.PREFIX ? 2 : 4);
    const response = ScriptValidation.hex(rule.response, `规则 ${index + 1} response`, 2);
    const mask = rule.mode === MatchMode.MASK ? ScriptValidation.hex(rule.mask, 'mask') : '';
    if (rule.mode === MatchMode.MASK && mask.length !== command.length) {
      throw new Error('mask 必须与 command 等长');
    }
    const result = ApduRule.clone(rule);
    result.command = command;
    result.response = response;
    result.mask = mask;
    return result;
  }

  static cards(cards: CardScript[], allowEmpty: boolean = false): CardScript[] {
    const min = allowEmpty ? 0 : 1;
    if (cards.length < min || cards.length > 200) {
      throw new Error('每个文件须包含 1–200 张卡片');
    }
    const normalized: CardScript[] = [];
    for (const card of cards) {
      normalized.push(ScriptValidation.card(card));
    }
    const ids: string[] = [];
    for (const card of normalized) {
      ids.push(card.id);
    }
    if (new Set<string>(ids).size !== ids.length) {
      throw new Error('文件中存在重复卡片 ID');
    }
    return normalized;
  }

  static generatedId(json: string): string {
    const md = cryptoFramework.createMd('SHA256');
    const encoder = new util.TextEncoder();
    const blob: cryptoFramework.DataBlob = { data: encoder.encodeInto(json) };
    md.updateSync(blob);
    const digest = md.digestSync();
    let hexText = '';
    for (let index = 0; index < digest.data.length && index < 10; index++) {
      const digit = digest.data[index].toString(16);
      hexText += digit.length < 2 ? `0${digit}` : digit;
    }
    return `import-${hexText}`;
  }

  static card(card: CardScript): CardScript {
    const name = card.name.trim();
    if (name.length === 0 || name.length > 120) {
      throw new Error('卡片名称须为 1–120 个字符');
    }
    let id = card.id;
    if (id.length === 0) {
      id = ScriptValidation.generatedId(JSON.stringify(ScriptCodec.cardJson(card)));
    }
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(id)) {
      throw new Error('卡片 ID 仅允许字母、数字、下划线、点和连字符');
    }
    const aids: string[] = [];
    for (const aid of card.aids) {
      const normalized = ScriptValidation.hex(aid, 'AID', 5);
      if (!aids.includes(normalized)) {
        aids.push(normalized);
      }
    }
    if (aids.length === 0 || aids.length > 16) {
      throw new Error('须包含 1–16 个 AID，每个 5–16 字节');
    }
    for (const aid of aids) {
      if (aid.length > 32) {
        throw new Error('须包含 1–16 个 AID，每个 5–16 字节');
      }
    }
    const rules: ApduRule[] = [];
    for (let index = 0; index < card.rules.length; index++) {
      rules.push(ScriptValidation.rule(card.rules[index], index));
    }
    if (rules.length < 1 || rules.length > 1000) {
      throw new Error('每张卡须包含 1–1000 条规则');
    }
    const result = CardScript.clone(card);
    result.id = id;
    result.name = name;
    result.scheme = card.scheme.trim();
    result.aids = aids;
    result.rules = rules;
    result.fallback = ScriptValidation.hex(card.fallback, 'fallback', 2);
    result.metadata = CardMetadataCodec.validate(card.metadata);
    return result;
  }
}
