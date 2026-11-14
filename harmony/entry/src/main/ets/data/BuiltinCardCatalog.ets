import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { CardPack } from '../core/CardPack';
import { CardPackCodec } from '../core/CardPackCodec';

/** 内置文件各自形成卡包，文件顺序不决定首页默认选择。 */
export class BuiltinCardCatalog {
  static readonly assetNames: string[] = ['demo-cards.json'];

  private static readAsset(context: common.Context, name: string): string {
    const data: Uint8Array = context.resourceManager.getRawFileContentSync(name);
    const decoder = util.TextDecoder.create('utf-8');
    return decoder.decodeToString(data);
  }

  /** 按资源文件清单解析内置只读卡包，并检查包 ID 和组合卡片 key 不重复。 */
  static loadPacks(context: common.Context): CardPack[] {
    const packs: CardPack[] = [];
    for (const name of BuiltinCardCatalog.assetNames) {
      packs.push(CardPackCodec.parse(BuiltinCardCatalog.readAsset(context, name), '导入卡包', true, false));
    }
    const ids: string[] = [];
    for (const pack of packs) {
      ids.push(pack.id);
    }
    if (new Set<string>(ids).size !== ids.length) {
      throw new Error('内置卡包 ID 重复');
    }
    const keys: string[] = [];
    for (const pack of packs) {
      for (const card of pack.cards) {
        keys.push(card.key);
      }
    }
    if (new Set<string>(keys).size !== keys.length) {
      throw new Error('内置卡片 ID 重复');
    }
    return packs;
  }
}
