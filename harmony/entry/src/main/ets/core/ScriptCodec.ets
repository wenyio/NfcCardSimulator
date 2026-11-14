import { util } from '@kit.ArkTS';
import { ApduRule, CardScript, MatchMode } from './CardScript';
import { CardMetadata } from './CardMetadata';
import { CardMetadataCodec } from './CardMetadataCodec';
import { RuleCodec } from './RuleCodec';
import { ScriptValidation } from './ScriptValidation';
import { Json } from './Json';

interface RawCardJson {
  id: string;
  name: string;
  scheme: string;
  aids: string[];
  description: string;
  ordered: boolean;
  fallback: string;
  rules: Object[];
  metadata?: Object;
}

interface RawCardsJson {
  schemaVersion: number;
  cards: Object[];
}

export class ScriptCodec {
  static readonly MAX_BYTES: number = 2 * 1024 * 1024;

  static byteLength(text: string): number {
    const encoder = new util.TextEncoder();
    return encoder.encodeInto(text).length;
  }

  static checkSize(text: string): void {
    if (ScriptCodec.byteLength(text) > ScriptCodec.MAX_BYTES) {
      throw new Error('文件超过 2 MB');
    }
  }

  static parse(text: string, builtIn: boolean = false, allowEmpty: boolean = false): CardScript[] {
    ScriptCodec.checkSize(text);
    const trimmed = text.replace(/^\uFEFF/, '').trim();
    let objects: Object[] | null = null;
    if (trimmed.startsWith('[')) {
      objects = Json.asArray(Json.parse(trimmed));
      if (objects === null) {
        throw new Error('JSON 数组格式错误');
      }
    } else {
      const root = Json.asRecord(Json.parse(trimmed));
      if (root === null) {
        throw new Error('JSON 对象格式错误');
      }
      objects = Json.asArray(root['cards']);
      if (objects === null) {
        objects = [root];
      }
    }
    const min = allowEmpty ? 0 : 1;
    if (objects.length < min || objects.length > 200) {
      throw new Error('每个文件须包含 1–200 张卡片');
    }
    const cards: CardScript[] = [];
    for (const item of objects) {
      cards.push(ScriptCodec.parseCard(item, builtIn));
    }
    const ids: string[] = [];
    for (const card of cards) {
      ids.push(card.id);
    }
    if (new Set<string>(ids).size !== ids.length) {
      throw new Error('文件中存在重复卡片 ID');
    }
    return cards;
  }

  private static parseCard(item: Object, builtIn: boolean): CardScript {
    const record = Json.asRecord(item);
    if (record === null) {
      throw new Error('卡片须为 JSON 对象');
    }
    let id = Json.str(record['id'], '');
    if (id.length === 0) {
      id = ScriptValidation.generatedId(JSON.stringify(record));
    }
    const aidArray = Json.asArray(record['aids']);
    if (aidArray === null) {
      throw new Error('卡片缺少 aids 数组');
    }
    const aids: string[] = [];
    for (const aid of aidArray) {
      aids.push(Json.str(aid, ''));
    }
    const rulesArray = Json.asArray(record['rules']);
    if (rulesArray === null) {
      throw new Error('卡片缺少 rules 数组');
    }
    const rules: ApduRule[] = [];
    for (let index = 0; index < rulesArray.length; index++) {
      rules.push(ScriptCodec.parseRule(rulesArray[index], index));
    }
    const card = new CardScript(id, Json.str(record['name'], '自定义卡片'),
      Json.str(record['scheme'], '自定义'), aids, Json.str(record['description'], ''),
      rules, Json.bool(record['ordered'], false), Json.str(record['fallback'], '6D00'),
      builtIn, CardMetadataCodec.parse(record));
    return ScriptValidation.card(card);
  }

  private static parseRule(item: Object, index: number): ApduRule {
    const record = Json.asRecord(item);
    if (record === null) {
      throw new Error(`规则 ${index + 1} 须为 JSON 对象`);
    }
    const mode = RuleCodec.modeOf(record);
    if (mode === MatchMode.MASK && record['mask'] === undefined) {
      throw new Error(`规则 ${index + 1} 缺少 mask`);
    }
    return RuleCodec.decode(record, mode);
  }

  static cardJson(card: CardScript): RawCardJson {
    const rules: Object[] = [];
    for (const rule of card.rules) {
      rules.push(RuleCodec.encode(rule, false));
    }
    const raw: RawCardJson = {
      id: card.id,
      name: card.name,
      scheme: card.scheme,
      aids: card.aids.slice(),
      description: card.description,
      ordered: card.ordered,
      fallback: card.fallback,
      rules: rules
    };
    if (!card.metadata.isEmpty) {
      raw.metadata = CardMetadataCodec.encode(card.metadata);
    }
    return raw;
  }

  static json(cards: CardScript[]): RawCardsJson {
    const list: Object[] = [];
    for (const card of cards) {
      list.push(ScriptCodec.cardJson(card));
    }
    return { schemaVersion: 1, cards: list };
  }

  static encode(cards: CardScript[]): string {
    return JSON.stringify(ScriptCodec.json(cards), null, 2);
  }
}
