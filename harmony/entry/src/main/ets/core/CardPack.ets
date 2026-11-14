import { CardScript } from './CardScript';

export class CardPack {
  id: string;
  name: string;
  description: string;
  cards: CardScript[];
  builtIn: boolean;

  constructor(id: string, name: string, description: string = '',
    cards: CardScript[] = [], builtIn: boolean = false) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.cards = cards;
    this.builtIn = builtIn;
  }

  bound(): CardPack {
    const cards: CardScript[] = [];
    for (const card of this.cards) {
      const bound = CardScript.clone(card);
      bound.packId = this.id;
      bound.builtIn = this.builtIn;
      cards.push(bound);
    }
    return new CardPack(this.id, this.name, this.description, cards, this.builtIn);
  }

  static clone(pack: CardPack): CardPack {
    const cards: CardScript[] = [];
    for (const card of pack.cards) {
      cards.push(CardScript.clone(card));
    }
    return new CardPack(pack.id, pack.name, pack.description, cards, pack.builtIn);
  }
}

export class CardPacks {
  static readonly PERSONAL: string = 'personal';
  static readonly DEMO: string = 'connectivity-demo';

  static validateId(id: string): void {
    if (!/^[A-Za-z0-9_.-]{1,100}$/.test(id)) {
      throw new Error('卡包 ID 仅允许 1–100 位字母、数字、下划线、点和连字符');
    }
  }

  static validateName(name: string): string {
    const value = name.trim();
    if (value.length < 1 || value.length > 80 || /[\x00-\x1F\x7F]/.test(value)) {
      throw new Error('卡包名称须为 1–80 个字符，不能包含控制字符');
    }
    return value;
  }
}
