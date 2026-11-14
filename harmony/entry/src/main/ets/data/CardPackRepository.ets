import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { Files } from '../common/Files';
import { CardPack, CardPacks } from '../core/CardPack';
import { CardPackCodec } from '../core/CardPackCodec';
import { CardScript } from '../core/CardScript';
import { Json } from '../core/Json';
import { ScriptCodec } from '../core/ScriptCodec';
import { BuiltinCardCatalog } from './BuiltinCardCatalog';

/** 用户包一次原子写入，避免导入留下半个包。 */
export class CardPackRepository {
  private readonly path: string;
  private readonly builtins: CardPack[];
  private custom: CardPack[];
  readError: string = '';

  constructor(context: common.Context) {
    this.path = `${context.filesDir}/card-packs.json`;
    this.builtins = BuiltinCardCatalog.loadPacks(context);
    this.custom = this.load();
  }

  /** 返回内置包与用户包组成的列表快照；调用方通过仓库方法提交修改。 */
  packs(): CardPack[] {
    return this.builtins.concat(this.custom);
  }

  /** 按稳定包 ID 查找，找不到返回 null。 */
  find(id: string): CardPack | null {
    for (const pack of this.packs()) {
      if (pack.id === id) {
        return pack;
      }
    }
    return null;
  }

  /** 展开所有包中的卡片，包含内置只读卡和用户卡。 */
  cards(): CardScript[] {
    const result: CardScript[] = [];
    for (const pack of this.packs()) {
      for (const card of pack.cards) {
        result.push(card);
      }
    }
    return result;
  }

  private load(): CardPack[] {
    try {
      if (Files.exists(this.path) || Files.exists(`${this.path}.bak`)) {
        const text = Files.readText(Files.exists(this.path) ? this.path : `${this.path}.bak`);
        const root = Json.asRecord(Json.parse(text));
        if (root === null) {
          throw new Error('卡包存储格式错误');
        }
        if (Json.num(root['storageVersion'], 0) !== 1) {
          throw new Error('不支持的本地存储版本');
        }
        const packsArray = Json.asArray(root['packs']);
        if (packsArray === null) {
          throw new Error('卡包存储缺少 packs');
        }
        const loaded: CardPack[] = [];
        for (const item of packsArray) {
          loaded.push(CardPackCodec.parse(JSON.stringify(item), '导入卡包', false, true));
        }
        this.validate(loaded);
        return loaded;
      }
      const initial: CardPack[] = [new CardPack(CardPacks.PERSONAL, '自建卡片').bound()];
      this.write(initial);
      return initial;
    } catch (error) {
      this.readError = `卡包读取失败，原文件保留，修复前禁止写入：${(error as Error).message}`;
      return [new CardPack(CardPacks.PERSONAL, '自建卡片')];
    }
  }

  /** 校验用户包总量、标识冲突、必需个人包和每包内容；不写文件、不替换内存状态。 */
  private validate(packs: CardPack[]): void {
    const ids: string[] = [];
    for (const pack of packs) {
      ids.push(pack.id);
    }
    if (packs.length > 100 || new Set<string>(ids).size !== ids.length) {
      throw new Error('卡包数量过多或 ID 重复');
    }
    for (const pack of packs) {
      for (const builtin of this.builtins) {
        if (builtin.id === pack.id) {
          throw new Error('不能覆盖内置卡包');
        }
      }
    }
    let hasPersonal = false;
    for (const pack of packs) {
      if (pack.id === CardPacks.PERSONAL) {
        hasPersonal = true;
      }
    }
    if (!hasPersonal) {
      throw new Error('自建卡片包缺失');
    }
    for (const pack of packs) {
      CardPackCodec.validate(pack);
    }
    let total = 0;
    for (const pack of packs) {
      total += pack.cards.length;
    }
    if (total > 2000) {
      throw new Error('用户卡片总数最多 2000 张');
    }
  }

  /** 将完整用户包集合编码后检查 20 MB 上限，再原子提交。 */
  private write(packs: CardPack[]): void {
    const packJsons: Object[] = [];
    for (const pack of packs) {
      packJsons.push(CardPackCodec.json(pack));
    }
    const root: Record<string, Object> = { 'storageVersion': 1, 'packs': packJsons };
    const text = JSON.stringify(root);
    if (ScriptCodec.byteLength(text) > 20 * 1024 * 1024) {
      throw new Error('用户卡包累计数据超过 20 MB');
    }
    Files.writeTextAtomic(this.path, text);
  }

  /** 统一写入出口：读取失败时禁止覆盖，校验和原子写入均成功后才更新内存 custom。 */
  private commit(packs: CardPack[]): void {
    if (this.readError.length > 0) {
      throw new Error(this.readError);
    }
    this.validate(packs);
    this.write(packs);
    // 文件提交成功才切换读者可见的内存集合；任何前置异常都会停在旧状态。
    this.custom = packs;
  }

  /** 生成随机包 ID 并提交空用户包；校验或文件写入失败时不更新内存包列表。 */
  createPack(name: string, description: string): CardPack {
    const pack = new CardPack(`pack-${util.generateRandomUUID(false)}`, CardPacks.validateName(name),
      description.trim());
    this.commit(this.custom.concat([pack]));
    return pack;
  }

  /**
   * 校验目标包、原卡身份和目标 ID 冲突，再构造整个新集合一次提交。
   * 跨包移动与同包编辑共用此事务；任何检查或写入失败均不先移除原卡。
   */
  saveEditedCard(card: CardScript, originalKey: string | null): void {
    let targetOk = false;
    for (const pack of this.custom) {
      if (pack.id === card.packId) {
        targetOk = true;
      }
    }
    if (card.builtIn || !targetOk) {
      throw new Error('目标卡包不存在或只读');
    }
    if (originalKey !== null) {
      let original: CardScript | null = null;
      for (const pack of this.custom) {
        for (const item of pack.cards) {
          if (item.key === originalKey) {
            original = item;
          }
        }
      }
      if (original === null || original.id !== card.id) {
        throw new Error('原卡片不存在或已变化，请重新打开');
      }
    }
    for (const pack of this.custom) {
      for (const item of pack.cards) {
        if (item.key === card.key && item.key !== originalKey) {
          throw new Error('目标卡包已存在相同编号的卡片，请选择其他卡包或复制卡片');
        }
      }
    }
    const next: CardPack[] = [];
    for (const pack of this.custom) {
      let samePack = false;
      for (const item of pack.cards) {
        if (item.key === originalKey) {
          samePack = true;
        }
      }
      samePack = samePack && pack.id === card.packId;
      let updated: CardScript[];
      if (samePack) {
        updated = pack.cards.map((item: CardScript): CardScript => item.key === originalKey ? card : item);
      } else {
        const remaining = pack.cards.filter((item: CardScript): boolean => item.key !== originalKey);
        updated = pack.id === card.packId ? remaining.concat([card]) : remaining;
      }
      const copy = CardPack.clone(pack);
      copy.cards = updated;
      next.push(copy.bound());
    }
    this.commit(next);
  }

  /** 按调用方选择新建或整包更新，拒绝覆盖内置包和个人包；成功后返回绑定归属的新包。 */
  importPack(incoming: CardPack, targetId: string | null): CardPack {
    const id = targetId !== null ? targetId : incoming.id;
    const old = this.find(id);
    if (id === CardPacks.PERSONAL || (old !== null && old.builtIn)) {
      throw new Error('内置卡包和自建卡片不能整包替换');
    }
    if (targetId === null && old !== null) {
      throw new Error('卡包 ID 已存在，请选择更新或新建副本');
    }
    if (targetId !== null && old === null) {
      throw new Error('待更新卡包不存在');
    }
    const pack = CardPack.clone(incoming);
    pack.id = id;
    pack.builtIn = false;
    const bound = pack.bound();
    this.commit(this.custom.filter((item: CardPack): boolean => item.id !== id).concat([bound]));
    return bound;
  }

  /** 仅允许删除用户卡，构造过滤后的包集合后原子提交；找不到可删卡时抛异常。 */
  deleteCard(key: string): void {
    let found = false;
    for (const pack of this.custom) {
      for (const card of pack.cards) {
        if (card.key === key) {
          found = true;
        }
      }
    }
    if (!found) {
      throw new Error('内置卡片不能删除');
    }
    this.commit(this.custom.map((pack: CardPack): CardPack => {
      const copy = CardPack.clone(pack);
      copy.cards = pack.cards.filter((card: CardScript): boolean => card.key !== key);
      return copy;
    }));
  }

  /** 仅修改用户包名称，保留 ID 和卡片；名称校验或写入失败时保留原数据。 */
  rename(id: string, name: string): void {
    let found = false;
    for (const pack of this.custom) {
      if (pack.id === id) {
        found = true;
      }
    }
    if (!found) {
      throw new Error('内置卡包不能重命名');
    }
    const value = CardPacks.validateName(name);
    this.commit(this.custom.map((pack: CardPack): CardPack => {
      if (pack.id !== id) {
        return pack;
      }
      const copy = CardPack.clone(pack);
      copy.name = value;
      return copy;
    }));
  }

  /** 删除指定用户包；个人包和内置包不可删除，实际提交仍经过统一校验。 */
  delete(id: string): void {
    let found = false;
    for (const pack of this.custom) {
      if (pack.id === id) {
        found = true;
      }
    }
    if (id === CardPacks.PERSONAL || !found) {
      throw new Error('该卡包不能删除');
    }
    this.commit(this.custom.filter((pack: CardPack): boolean => pack.id !== id));
  }
}
