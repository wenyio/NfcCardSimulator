import { util } from '@kit.ArkTS';
import { CardPack, CardPacks } from './CardPack';
import { CardScript } from './CardScript';
import { ScriptCodec } from './ScriptCodec';
import { ScriptValidation } from './ScriptValidation';
import { Json } from './Json';

interface RawPackInfo {
  id: string;
  name: string;
  description: string;
}

interface RawPackJson {
  schemaVersion: number;
  cards: Object[];
  pack: RawPackInfo;
}

export class CardPackCodec {
  static parse(text: string, defaultName: string = '导入卡包', builtIn: boolean = false,
    allowEmpty: boolean = false): CardPack {
    if (ScriptCodec.byteLength(text) > ScriptCodec.MAX_BYTES) {
      throw new Error('文件超过 2 MB');
    }
    const clean = text.replace(/^\uFEFF/, '').trim();
    let root: Record<string, Object> | null = null;
    if (clean.startsWith('{')) {
      root = Json.asRecord(Json.parse(clean));
      if (root === null) {
        throw new Error('JSON 对象格式错误');
      }
    }
    const version = root === null ? 1 : Json.num(root['schemaVersion'], 1);
    if (version < 1 || version > 2) {
      throw new Error(`不支持的卡包 schemaVersion：${version}`);
    }
    const info = root === null ? null : Json.asRecord(root['pack']);
    if (version === 2 && (info === null || root === null || root['cards'] === undefined)) {
      throw new Error('版本 2 须包含 pack 和 cards');
    }
    let id: string;
    if (info !== null && info['id'] !== undefined) {
      id = Json.str(info['id'], '');
    } else if (info === null) {
      id = `pack-${util.generateRandomUUID(false)}`;
    } else {
      throw new Error('卡包缺少 id');
    }
    CardPacks.validateId(id);
    const name = CardPacks.validateName(info !== null && info['name'] !== undefined ?
      Json.str(info['name'], '') : defaultName);
    const description = info !== null ? Json.str(info['description'], '') : '';
    if (description.length > 4000) {
      throw new Error('卡包说明过长');
    }
    return new CardPack(id, name, description,
      ScriptCodec.parse(clean, builtIn, allowEmpty), builtIn).bound();
  }

  static json(pack: CardPack): RawPackJson {
    const cards: Object[] = [];
    for (const card of pack.cards) {
      cards.push(ScriptCodec.cardJson(card));
    }
    return {
      schemaVersion: 2,
      cards: cards,
      pack: { id: pack.id, name: pack.name, description: pack.description }
    };
  }

  static encode(pack: CardPack): string {
    return JSON.stringify(CardPackCodec.json(pack), null, 2);
  }

  static validate(pack: CardPack): void {
    ScriptCodec.checkSize(CardPackCodec.encode(pack));
    CardPacks.validateId(pack.id);
    CardPacks.validateName(pack.name);
    if (pack.description.length > 4000) {
      throw new Error('卡包说明过长');
    }
    ScriptValidation.cards(pack.cards, true);
  }
}
