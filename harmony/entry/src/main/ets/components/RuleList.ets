import { ApduRule, MatchMode } from '../core/CardScript';
import { MatchingLabels } from './MatchingLabels';
import { Ui } from './Ui';

/** 规则显示名；空白标签回退为未命名规则。 */
function ruleTitle(rule: ApduRule): string {
  return rule.label.length > 0 ? rule.label : '未命名规则';
}

/** 单条规则的可折叠行；展开只影响视图，不触发匹配引擎或改变执行步骤。 */
@Component
export struct RuleRow {
  @Prop ruleIndex: number = 0;
  @Prop ruleTitleText: string = '';
  @Prop ruleModeLabel: string = '';
  @Prop ruleCommand: string = '';
  @Prop ruleResponse: string = '';
  @Prop ruleMask: string = '';
  @Prop ruleIsMask: boolean = false;
  @State expanded: boolean = false;

  private detailText(): string {
    const parts: string[] = [`T: ${this.ruleCommand}`, `C: ${this.ruleResponse}`];
    if (this.ruleIsMask) {
      parts.push(`MASK: ${this.ruleMask}`);
    }
    return parts.join('\n');
  }

  build() {
    Column() {
      Row() {
        Text(`${this.ruleIndex}`)
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width(28)
        Text(this.ruleTitleText)
          .fontSize(14)
          .fontColor(Ui.INK)
          .layoutWeight(1)
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.ruleModeLabel)
          .fontSize(12)
          .fontColor(Ui.MUTED)
          .padding({ left: 8, right: 4 })
        if (this.expanded) {
          Image($r('app.media.ic_expand_more'))
            .width(24)
            .height(24)
            .objectFit(ImageFit.Contain)
            .fillColor(Ui.MUTED)
            .accessibilityText('展开或收起')
            .rotate({ angle: 180 })
        } else {
          Image($r('app.media.ic_expand_more'))
            .width(24)
            .height(24)
            .objectFit(ImageFit.Contain)
            .fillColor(Ui.MUTED)
            .accessibilityText('展开或收起')
        }
      }
      .width('100%')
      .height(48)
      .onClick(() => {
        this.expanded = !this.expanded;
      })

      if (this.expanded) {
        Text(this.detailText())
          .fontSize(12)
          .fontFamily(Ui.MONO)
          .fontColor(Ui.INK)
          .padding({ left: 8, top: 4, right: 8, bottom: 12 })
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .textAlign(TextAlign.Start)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}

/** 规则摘要列表；长 APDU 仅在展开后显示，避免挤占脚本总览。 */
@Component
export struct RuleList {
  @Prop rules: ApduRule[] = [];

  build() {
    Column() {
      ForEach(this.rules, (rule: ApduRule, index: number) => {
        Column() {
          if (index > 0) {
            Divider()
              .color(Ui.DIVIDER)
              .strokeWidth(1)
          }
          RuleRow({
            ruleIndex: index + 1,
            ruleTitleText: ruleTitle(rule),
            ruleModeLabel: MatchingLabels.mode(rule.mode),
            ruleCommand: rule.command,
            ruleResponse: rule.response,
            ruleMask: rule.mask,
            ruleIsMask: rule.mode === MatchMode.MASK
          })
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      }, (rule: ApduRule, index: number) =>
        `${index}-${rule.command}-${rule.response}-${rule.mode}-${rule.label}`)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
