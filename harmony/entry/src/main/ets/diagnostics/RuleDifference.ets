import { ApduRule, MatchMode } from '../core/CardScript';

/** 只解释指定候选规则的差异；诊断结果绝不参与选规则或修改回复。 */
export class RuleDifference {
  /**
   * 逐字节比较候选规则与实际命令，MASK 模式只比较掩码选中的位；最多显示 16 处差异。
   * 输入应已规范化且规则有效，界面字节编号从 1 开始，字符串偏移则从 0 开始。
   */
  static describe(rule: ApduRule, actual: string): string {
    const expected = rule.command;
    const differences: number[] = [];
    const count = Math.min(expected.length, actual.length);
    for (let index = 0; index < count; index += 2) {
      const mask = rule.mode === MatchMode.MASK ? parseInt(rule.mask.substring(index, index + 2), 16) : 255;
      if ((parseInt(expected.substring(index, index + 2), 16) & mask) !==
        (parseInt(actual.substring(index, index + 2), 16) & mask)) {
        differences.push(index);
      }
    }
    const parts: string[] = [];
    for (const index of differences.slice(0, 16)) {
      parts.push(`字节 ${index / 2 + 1}: 期望 ${expected.substring(index, index + 2)} / 实际 ${actual.substring(index, index + 2)}`);
    }
    const detail = parts.join('；');
    const length = expected.length !== actual.length ?
      `长度：期望 ${expected.length / 2}，实际 ${actual.length / 2} 字节。` : '';
    const tail = differences.length > 16 ? '（其余差异省略）' : '';
    return `${rule.mode} · ${length}${detail}${tail}`;
  }
}
