export class CardSchemes {
  static readonly fixed: string[] = ['Amex', 'Discover', 'JCB', 'Mastercard', 'UnionPay', 'Visa', '其他'];
  private static readonly reserved: string[] = ['全部', '自定义', '＋ 添加卡组织'];

  static options(names: string[]): string[] {
    const result: string[] = [];
    const seen: Set<string> = new Set<string>();
    const all: string[] = CardSchemes.fixed.concat(names);
    for (const raw of all) {
      const value = raw.trim();
      if (value.length === 0) {
        continue;
      }
      const key = value.toLowerCase();
      if (!seen.has(key)) {
        seen.add(key);
        result.push(value);
      }
    }
    return result;
  }

  static canonical(name: string, names: string[] = []): string {
    const trimmed = name.trim();
    for (const option of CardSchemes.options(names)) {
      if (option.toLowerCase() === trimmed.toLowerCase()) {
        return option;
      }
    }
    return trimmed;
  }

  static validateNew(name: string, existing: string[]): string {
    const value = name.trim();
    if (value.length === 0 || value.length > 40) {
      throw new Error('卡组织名称须为 1–40 个字符');
    }
    if (/[\x00-\x1F\x7F]/.test(value)) {
      throw new Error('卡组织名称不能包含换行或控制字符');
    }
    for (const reserved of CardSchemes.reserved) {
      if (reserved.toLowerCase() === value.toLowerCase()) {
        throw new Error('该名称已用于页面分类，请换一个名称');
      }
    }
    for (const option of CardSchemes.options(existing)) {
      if (option.toLowerCase() === value.toLowerCase()) {
        throw new Error('该卡组织已存在，请从下拉列表选择');
      }
    }
    return value;
  }

  static categories(names: string[]): string[] {
    const result: string[] = ['全部'];
    for (const option of CardSchemes.options(names)) {
      if (CardSchemes.reserved.includes(option)) {
        continue;
      }
      let used = false;
      for (const name of names) {
        if (name.toLowerCase() === option.toLowerCase()) {
          used = true;
          break;
        }
      }
      if (used) {
        result.push(option);
      }
    }
    return result;
  }
}
