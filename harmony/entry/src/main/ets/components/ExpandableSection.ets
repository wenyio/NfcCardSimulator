import { Ui } from './Ui';

/** 默认折叠的标题与正文容器；点击只切换可见性，不影响业务状态。 */
@Component
export struct ExpandableSection {
  @Prop sectionTitle: string = '';
  @State expanded: boolean = false;
  @BuilderParam contentBuilder: () => void;

  build() {
    Column() {
      Row() {
        Text(this.sectionTitle)
          .fontSize(14)
          .fontColor(Ui.BLUE)
          .layoutWeight(1)
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
        Column() {
          this.contentBuilder()
        }
        .width('100%')
        .alignItems(HorizontalAlign.Start)
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
