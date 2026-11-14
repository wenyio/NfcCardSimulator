import { AppStore } from '../common/AppStore';
import { CardSchemeStore } from '../data/CardSchemeStore';
import { Ui } from './Ui';

/** 新增组织独立持久化；即使编辑卡片时取消，也可在下次选择。 */
@Component
export struct SchemeAddSheet {
  onAdded: (scheme: string) => void = () => {};
  onClose: () => void = () => {};
  schemes: CardSchemeStore = AppStore.getInstance().store.schemes;
  @State nameText: string = '';
  @State errorText: string = '';

  private confirmAdd(): void {
    try {
      const value = this.schemes.add(this.nameText);
      this.onAdded(value);
    } catch (error) {
      this.errorText = (error as Error).message;
    }
  }

  build() {
    Column() {
      Text('添加卡组织')
        .fontSize(18)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .width('100%')

      Text('卡组织名称')
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 12 })
      TextInput({ text: $$this.nameText, placeholder: '卡组织名称' })
        .fontSize(14)
        .width('100%')
        .margin({ top: 4 })

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
        Text('添加')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding(12)
          .onClick(() => {
            this.confirmAdd();
          })
      }
      .width('100%')
      .alignItems(VerticalAlign.Center)
    }
    .width('100%')
    .padding({ left: 20, right: 20, top: 16, bottom: 8 })
    .backgroundColor(Ui.WHITE)
    .alignItems(HorizontalAlign.Start)
  }
}
