import { ApduRule, CardScript, Exchange } from './CardScript';
import { Hex } from './Hex';
import { DolDiagnostics } from '../diagnostics/DolDiagnostics';
import { RuleDifference } from '../diagnostics/RuleDifference';

export class ScriptEngine {
  readonly script: CardScript;
  private cursor: number = 0;
  private dol: DolDiagnostics = new DolDiagnostics();

  constructor(script: CardScript) {
    this.script = script;
  }

  reset(): void {
    this.cursor = 0;
    this.dol.reset();
  }

  exchange(raw: string): Exchange {
    const command = Hex.clean(raw);
    if (!Hex.valid(command) || command.length < 8 || command.length > 131080) {
      return new Exchange('6700', 'APDU 长度或格式错误', false);
    }
    this.dol.beforeCommand(command);
    let rule: ApduRule | null = null;
    if (this.script.ordered) {
      const step = this.cursor < this.script.rules.length ? this.script.rules[this.cursor] : null;
      if (step !== null && step.matches(command)) {
        rule = step;
      }
    } else {
      for (const candidate of this.script.rules) {
        if (candidate.matches(command)) {
          rule = candidate;
          break;
        }
      }
    }
    if (rule === null) {
      let expected: ApduRule | null = null;
      if (this.script.ordered) {
        expected = this.cursor < this.script.rules.length ? this.script.rules[this.cursor] : null;
      } else if (command.startsWith('80A8') || command.startsWith('80AE')) {
        const header = command.substring(0, 8);
        const candidates = this.script.rules.filter((item: ApduRule): boolean =>
          item.command.substring(0, 8) === header);
        if (candidates.length === 1) {
          expected = candidates[0];
        }
      }
      let detail = '';
      if (expected !== null) {
        const parts: string[] = [];
        const difference = RuleDifference.describe(expected, command);
        const dolText = this.dol.describe(expected, command, false);
        if (difference.length > 0) {
          parts.push(difference);
        }
        if (dolText.length > 0) {
          parts.push(dolText);
        }
        detail = parts.join('\n');
      } else {
        detail = this.script.ordered ? '所有步骤已执行，请重置会话' :
          `共 ${this.script.rules.length} 条规则，无唯一诊断候选；可切换顺序模式定位差异`;
      }
      return new Exchange(this.script.fallback,
        this.script.ordered ? `步骤 ${this.cursor + 1} 未匹配` : '无匹配规则', false, detail);
    }
    let index: number;
    if (this.script.ordered) {
      index = this.cursor;
      this.cursor++;
    } else {
      index = this.script.rules.indexOf(rule);
    }
    const detail = this.dol.describe(rule, command, true);
    this.dol.observe(command, rule.response);
    const label = rule.label.length > 0 ? rule.label : `规则 ${index + 1}`;
    return new Exchange(rule.response, label, true, detail);
  }
}
