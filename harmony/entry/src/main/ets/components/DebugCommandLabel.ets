import { ApduRule } from '../core/CardScript';
import { Hex } from '../core/Hex';

/** 仅用于展示命令含义，不参与规则匹配，也不推断私有 CLA 的指令。 */
export class DebugCommandLabel {
  /** 根据已知命令头生成下拉摘要，未知命令使用规则标签；编号从 1 展示，不参与匹配。 */
  static format(index: number, rule: ApduRule): string {
    const command = Hex.clean(rule.command);
    let name: string = '';
    if (command.length >= 8 && Hex.valid(command)) {
      name = DebugCommandLabel.commandName(command.substring(0, 4));
    }
    const title = name.length > 0 ? name :
      (rule.label.length > 0 ? rule.label : `规则 ${index + 1}`);
    const header = command.substring(0, 8);
    const suffix = command.length > 8 ? '…' : '';
    return `${index + 1} · ${title} · ${header}${suffix}`;
  }

  private static commandName(head: string): string {
    if (head === '00A4') {
      return 'SELECT';
    }
    if (head === '80A8') {
      return 'GPO';
    }
    if (head === '00B2') {
      return 'READ RECORD';
    }
    if (head === '80AE') {
      return 'GENERATE AC';
    }
    if (head === '0084') {
      return 'GET CHALLENGE';
    }
    if (head === '0088') {
      return 'INTERNAL AUTHENTICATE';
    }
    if (head === '0082') {
      return 'EXTERNAL AUTHENTICATE';
    }
    if (head === '80CA') {
      return 'GET DATA';
    }
    return '';
  }
}
