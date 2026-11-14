import { CardMetadata } from '../core/CardMetadata';
import { CardScript } from '../core/CardScript';
import { DescriptionText } from './DescriptionText';
import { ExpandableSection } from './ExpandableSection';
import { Ui } from './Ui';

/** 场景正文只显示有效摘要，参考参数与脚本说明按需展开。 */
@Component
export struct ScenarioPanel {
  @Prop @Watch('onCardChanged') card: CardScript =
    new CardScript('', '', '其他', [], '', [], false, '6D00', false, new CardMetadata(), '');
  @State visible: boolean = false;
  @State summaryText: string = '';
  @State expectedText: string = '';
  @State conditionRows: string[] = [];
  @State notesText: string = '';

  aboutToAppear(): void {
    this.recompute();
  }

  onCardChanged(): void {
    this.recompute();
  }

  /** 元数据仅展示，不参与模拟；原场景名与卡名相同时不重复展示。 */
  private recompute(): void {
    const metadata = this.card.metadata;
    const scenario = metadata.scenario;
    const name = this.card.name.trim();
    const dotIndex = this.card.name.indexOf(' · ');
    const suffix = dotIndex >= 0 ? this.card.name.substring(dotIndex + 3).trim() : '';
    const prefix = '原场景：';
    const stripped = scenario.purpose.startsWith(prefix) ?
      scenario.purpose.substring(prefix.length).trim() : scenario.purpose.trim();
    let purpose = scenario.purpose;
    if (stripped === name || stripped === suffix) {
      purpose = '';
    }
    const summaryParts: string[] = [];
    if (scenario.category.length > 0) {
      summaryParts.push(scenario.category);
    }
    if (purpose.length > 0) {
      summaryParts.push(purpose);
    }
    this.summaryText = summaryParts.join(' · ');
    this.expectedText = scenario.expectedResult.length > 0 ?
      `预期结果：${scenario.expectedResult}` : '';
    const rows: string[] = [];
    ScenarioPanel.addRow(rows, '运行条件（仅说明）', scenario.conditions);
    ScenarioPanel.addRow(rows, '参考金额（原配置单位）', scenario.amount);
    ScenarioPanel.addRow(rows, '原 PDOL 数据（非 DOL 定义）', scenario.legacyPdol);
    ScenarioPanel.addRow(rows, '原 CDOL 数据（非 DOL 定义）', scenario.legacyCdol);
    if (!this.card.builtIn) {
      ScenarioPanel.addRow(rows, '原卡片编号', metadata.source.originalCardId);
    }
    ScenarioPanel.addRow(rows, '参考配置', metadata.source.configId);
    this.conditionRows = rows;
    const notes: string[] = [];
    for (const warning of metadata.warnings) {
      if (warning.length > 0) {
        notes.push(warning);
      }
    }
    this.notesText = notes.join('\n');
    this.visible = this.summaryText.length > 0 || this.expectedText.length > 0 ||
      this.conditionRows.length > 0 || this.notesText.length > 0;
  }

  private static addRow(rows: string[], title: string, value: string): void {
    if (value.length > 0) {
      rows.push(`${title}\n${value}`);
    }
  }

  build() {
    if (this.visible) {
      Column() {
        Text('测试场景')
          .fontSize(18)
          .fontColor(Ui.INK)
          .fontWeight(FontWeight.Bold)
          .width('100%')

        if (this.summaryText.length > 0) {
          DescriptionText({ bodyText: this.summaryText })
        }
        if (this.expectedText.length > 0) {
          DescriptionText({ bodyText: this.expectedText })
        }
        if (this.conditionRows.length > 0) {
          ExpandableSection({ sectionTitle: '运行条件' }) {
            ForEach(this.conditionRows, (row: string) => {
              Text(row)
                .fontSize(13)
                .fontColor(Ui.INK)
                .padding({ top: 4, bottom: 4 })
                .copyOption(CopyOptions.LocalDevice)
                .width('100%')
                .textAlign(TextAlign.Start)
            }, (row: string) => row)
          }
        }
        if (this.notesText.length > 0) {
          ExpandableSection({ sectionTitle: '脚本说明' }) {
            Text(this.notesText)
              .fontSize(13)
              .fontColor(Ui.INK)
              .padding({ top: 4, bottom: 4 })
              .copyOption(CopyOptions.LocalDevice)
              .width('100%')
              .textAlign(TextAlign.Start)
          }
        }
      }
      .width('100%')
      .padding({ left: 16, top: 12, right: 16, bottom: 12 })
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ top: 12 })
      .alignItems(HorizontalAlign.Start)
    }
  }
}
