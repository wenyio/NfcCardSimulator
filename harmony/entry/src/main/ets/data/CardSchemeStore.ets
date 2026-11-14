import { common } from '@kit.AbilityKit';
import { preferences } from '@kit.ArkData';
import { CardScript } from '../core/CardScript';
import { CardSchemes } from '../core/CardSchemes';
import { Json } from '../core/Json';

/** 新增组织独立持久化，即使编辑卡片时取消，也可在下次选择。 */
export class CardSchemeStore {
  private readonly store: preferences.Preferences;
  private cardsProvider: () => CardScript[] = (): CardScript[] => [];

  constructor(context: common.Context) {
    this.store = preferences.getPreferencesSync(context, { name: 'card-schemes' });
  }

  /** 注入卡片来源；由 SimulatorStore 构造后设置，避免循环依赖。 */
  setCardsProvider(provider: () => CardScript[]): void {
    this.cardsProvider = provider;
  }

  /** 从偏好设置读取新增组织列表；读取失败返回空列表，不修改已保存内容。 */
  private saved(): string[] {
    try {
      const raw = this.store.getSync('names', '[]');
      const text = typeof raw === 'string' ? raw : '[]';
      const array = Json.asArray(Json.parse(text));
      if (array === null) {
        return [];
      }
      const result: string[] = [];
      for (const item of array) {
        if (typeof item === 'string') {
          result.push(item);
        }
      }
      return result;
    } catch (error) {
      return [];
    }
  }

  /** 合并独立保存的组织和卡片中使用的组织，生成统一去重选项。 */
  options(): string[] {
    const schemes: string[] = [];
    for (const card of this.cardsProvider()) {
      schemes.push(card.scheme);
    }
    return CardSchemes.options(this.saved().concat(schemes));
  }

  /** 校验并保存新增组织，返回规范名称；此保存独立于卡片草稿，取消编辑不会撤销它。 */
  add(name: string): string {
    const value = CardSchemes.validateNew(name, this.options());
    const names = this.saved().concat([value]);
    this.store.putSync('names', JSON.stringify(names));
    this.store.flushSync();
    return value;
  }
}
