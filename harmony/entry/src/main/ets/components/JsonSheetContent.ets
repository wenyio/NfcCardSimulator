import { Ui } from './Ui';

/** 弹层内只读展示 JSON 文本，自带标题行。 */
@Component
export struct JsonSheetContent {
  @Prop titleText: string = '';
  @Prop jsonText: string = '';

  build() {
    Column() {
      Text(this.titleText)
        .fontSize(16)
        .fontWeight(FontWeight.Bold)
        .fontColor(Ui.INK)
        .maxLines(1)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .padding({ left: 16, right: 16, top: 16, bottom: 8 })
        .width('100%')
      Divider()
        .color(Ui.DIVIDER)
      Scroll() {
        Text(this.jsonText)
          .fontSize(12)
          .fontFamily(Ui.MONO)
          .fontColor(Ui.INK)
          .copyOption(CopyOptions.LocalDevice)
          .padding(16)
          .width('100%')
          .textAlign(TextAlign.Start)
      }
      .align(Alignment.TopStart)
      .layoutWeight(1)
      .width('100%')
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Ui.WHITE)
  }
}
