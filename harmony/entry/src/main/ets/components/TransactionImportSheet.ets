import { AppStore } from '../common/AppStore';
import { CardScript } from '../core/CardScript';
import { CapturedTransaction } from '../importer/TransactionLogParser';
import { ImportEvidence } from '../importer/ImportEvidence';
import { Ui } from './Ui';

/** 每次选择一笔完整交易，明确确认 AID 后才转换为顺序固定回复脚本。 */
@Component
export struct TransactionImportSheet {
  transactions: CapturedTransaction[] | null = null;
  evidence: ImportEvidence | null = null;
  onSave: (card: CardScript) => void = () => {};
  onClose: () => void = () => {};
  @State selected: number = 0;
  @State nameText: string = '日志测试卡';
  @State schemeText: string = '自定义';
  @State aidsText: string = '';
  @State previewText: string = '';
  @State errorText: string = '';
  @State importEnabled: boolean = true;
  @State optionLabels: string[] = [];
  private transactionsData: CapturedTransaction[] = [];

  aboutToAppear(): void {
    const transactions = this.transactions;
    if (transactions === null || transactions.length === 0) {
      return;
    }
    this.transactionsData = transactions;
    const labels: string[] = [];
    for (let index = 0; index < transactions.length; index++) {
      labels.push(`交易 ${index + 1} · ${transactions[index].pairs.length} 组 · ` +
        `${transactions[index].issues.length} 个错误`);
    }
    this.optionLabels = labels;
    this.selected = 0;
    this.update();
  }

  /** 切换交易后更新提取的 AID 和限长预览；存在配对问题时禁用导入，完整规则不裁剪。 */
  private update(): void {
    if (this.selected >= this.transactionsData.length) {
      return;
    }
    const transaction = this.transactionsData[this.selected];
    this.aidsText = transaction.aids().join('\n');
    const parts: string[] = [];
    if (transaction.issues.length > 0) {
      parts.push(transaction.issues.join('\n'));
    }
    for (const pair of transaction.pairs) {
      parts.push(`行 ${pair.line}\nT: ${pair.command}\nC: ${pair.response}`);
    }
    this.previewText = parts.join('\n\n').substring(0, 16000);
    this.errorText = '';
    this.importEnabled = transaction.issues.length === 0 && transaction.pairs.length > 0;
  }

  private transactionOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    for (const label of this.optionLabels) {
      options.push({ value: label });
    }
    return options;
  }

  private selectedLabelText(): string {
    if (this.selected < this.optionLabels.length) {
      return this.optionLabels[this.selected];
    }
    return '';
  }

  private confirmImport(): void {
    if (this.selected >= this.transactionsData.length) {
      return;
    }
    try {
      const transaction = this.transactionsData[this.selected];
      const aids: string[] = [];
      for (const line of this.aidsText.split('\n')) {
        if (line.trim().length > 0) {
          aids.push(line.trim());
        }
      }
      let card = transaction.toCard(this.nameText, this.schemeText, aids);
      if (this.evidence !== null) {
        card = this.evidence.attach(card, 'T:/C: 交易日志');
      }
      this.onSave(card);
    } catch (error) {
      this.errorText = (error as Error).message;
    }
  }

  build() {
    Column() {
      Text('交易日志导入预览')
        .fontSize(18)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .width('100%')

      Select(this.transactionOptions())
        .selected(this.selected)
        .value(this.selectedLabelText())
        .width('100%')
        .margin({ top: 12 })
        .onSelect((index: number) => {
          this.selected = index;
          this.update();
        })

      TextInput({ text: $$this.nameText, placeholder: '导入卡片名称' })
        .fontSize(14)
        .width('100%')
        .margin({ top: 12 })

      TextInput({ text: $$this.schemeText, placeholder: '卡组织' })
        .fontSize(14)
        .width('100%')
        .margin({ top: 12 })

      Text('AID（每行一个；请核对 SELECT 提取结果）')
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 12 })
      TextArea({ text: $$this.aidsText })
        .fontFamily(Ui.MONO)
        .fontSize(12)
        .height(72)
        .width('100%')
        .margin({ top: 4 })

      Scroll() {
        Text(this.previewText)
          .fontSize(12)
          .fontFamily(Ui.MONO)
          .fontColor(Ui.INK)
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .textAlign(TextAlign.Start)
      }
      .align(Alignment.TopStart)
      .layoutWeight(1)
      .width('100%')
      .backgroundColor(Ui.FIELD_BG)
      .borderRadius(8)
      .margin({ top: 12 })

      if (this.errorText.length > 0) {
        Text(this.errorText)
          .fontSize(13)
          .fontColor(Ui.DANGER)
          .width('100%')
          .margin({ top: 8 })
      }

      Row() {
        Text('取消')
          .fontSize(15)
          .fontColor(Ui.MUTED)
          .padding(12)
          .onClick(() => {
            this.onClose();
          })
        Blank()
        Text('导入交易')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .enabled(this.importEnabled)
          .padding(12)
          .onClick(() => {
            this.confirmImport();
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .height('100%')
    .padding({ left: 20, right: 20, top: 16, bottom: 8 })
    .backgroundColor(Ui.WHITE)
    .alignItems(HorizontalAlign.Start)
  }
}
