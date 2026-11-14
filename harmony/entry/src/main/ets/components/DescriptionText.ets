import { Ui } from './Ui';

/** 按估算行数判断是否超过三行；长说明默认收起并提供展开入口。 */
function estimateLines(text: string): number {
  const segments = text.split('\n');
  let total = 0;
  for (const segment of segments) {
    const lines = Math.ceil(segment.length / 24);
    total += lines > 0 ? lines : 1;
  }
  return total;
}

/** 短说明直接显示；超过三行的说明默认收起，提供展开和收起入口。 */
@Component
export struct DescriptionText {
  @Prop @Watch('onBodyChanged') bodyText: string = '';
  @Prop bodyColor: string = Ui.MUTED;
  @Prop bodySize: number = 14;
  @State expanded: boolean = false;
  @State needsExpand: boolean = false;

  aboutToAppear(): void {
    this.needsExpand = estimateLines(this.bodyText) > 3;
  }

  onBodyChanged(): void {
    this.expanded = false;
    this.needsExpand = estimateLines(this.bodyText) > 3;
  }

  build() {
    Column() {
      if (this.needsExpand && !this.expanded) {
        Text(this.bodyText)
          .fontSize(this.bodySize)
          .fontColor(this.bodyColor)
          .maxLines(3)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .textAlign(TextAlign.Start)
      } else {
        Text(this.bodyText)
          .fontSize(this.bodySize)
          .fontColor(this.bodyColor)
          .copyOption(CopyOptions.LocalDevice)
          .width('100%')
          .textAlign(TextAlign.Start)
      }

      if (this.needsExpand) {
        if (this.expanded) {
          Text('收起说明')
            .fontSize(13)
            .fontColor(Ui.BLUE)
            .height(44)
            .width('100%')
            .textAlign(TextAlign.Start)
            .onClick(() => {
              this.expanded = false;
            })
        } else {
          Text('展开说明')
            .fontSize(13)
            .fontColor(Ui.BLUE)
            .height(44)
            .width('100%')
            .textAlign(TextAlign.Start)
            .onClick(() => {
              this.expanded = true;
            })
        }
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
