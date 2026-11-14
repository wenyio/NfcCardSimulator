import { CardMetadata } from './CardMetadata';

export enum MatchMode {
  EXACT = 'EXACT',
  MASK = 'MASK',
  PREFIX = 'PREFIX'
}

export class ApduRule {
  command: string;
  response: string;
  mode: MatchMode;
  mask: string;
  label: string;

  constructor(command: string = '', response: string = '', mode: MatchMode = MatchMode.EXACT,
    mask: string = '', label: string = '') {
    this.command = command;
    this.response = response;
    this.mode = mode;
    this.mask = mask;
    this.label = label;
  }

  static clone(rule: ApduRule): ApduRule {
    return new ApduRule(rule.command, rule.response, rule.mode, rule.mask, rule.label);
  }

  matches(input: string): boolean {
    if (this.mode === MatchMode.EXACT) {
      return input === this.command;
    }
    if (this.mode === MatchMode.PREFIX) {
      return input.startsWith(this.command);
    }
    if (this.mode === MatchMode.MASK) {
      if (input.length !== this.command.length) {
        return false;
      }
      for (let offset = 0; offset < this.command.length; offset += 2) {
        const bits = parseInt(this.mask.substring(offset, offset + 2), 16);
        const actual = parseInt(input.substring(offset, offset + 2), 16) & bits;
        const expected = parseInt(this.command.substring(offset, offset + 2), 16) & bits;
        if (actual !== expected) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}

export class CardScript {
  id: string;
  name: string;
  scheme: string;
  aids: string[];
  description: string;
  rules: ApduRule[];
  ordered: boolean;
  fallback: string;
  builtIn: boolean;
  metadata: CardMetadata;
  packId: string;

  constructor(id: string, name: string, scheme: string, aids: string[], description: string,
    rules: ApduRule[], ordered: boolean = false, fallback: string = '6D00',
    builtIn: boolean = false, metadata: CardMetadata = new CardMetadata(), packId: string = '') {
    this.id = id;
    this.name = name;
    this.scheme = scheme;
    this.aids = aids;
    this.description = description;
    this.rules = rules;
    this.ordered = ordered;
    this.fallback = fallback;
    this.builtIn = builtIn;
    this.metadata = metadata;
    this.packId = packId;
  }

  get key(): string {
    return this.packId.length === 0 ? this.id : `${this.packId}:${this.id}`;
  }

  static clone(card: CardScript): CardScript {
    const rules: ApduRule[] = [];
    for (const rule of card.rules) {
      rules.push(ApduRule.clone(rule));
    }
    return new CardScript(card.id, card.name, card.scheme, card.aids.slice(), card.description,
      rules, card.ordered, card.fallback, card.builtIn, CardMetadata.clone(card.metadata), card.packId);
  }
}

export class Exchange {
  readonly response: string;
  readonly rule: string;
  readonly matched: boolean;
  readonly difference: string;

  constructor(response: string, rule: string, matched: boolean, difference: string = '') {
    this.response = response;
    this.rule = rule;
    this.matched = matched;
    this.difference = difference;
  }
}
