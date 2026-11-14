import { AppStore } from '../common/AppStore';
import { CardPack } from '../core/CardPack';
import { SimulatorStore } from '../data/SimulatorStore';
import { Ui } from './Ui';

/** 新建用户包；名称校验或写入失败时保留输入并提示，不关闭表单。 */
@Component
export struct PackCreateSheet {
  store: SimulatorStore = AppStore.getInstance().store;
  onCreated: (pack: CardPack) => void = () => {};
  onMessage: (text: string) => void = () => {};
  onClose: () => void = () => {};
  @State nameText: string = '';
  @State descText: string = '';

  private confirmCreate(): void {
    try {
      const pack = this.store.createPack(this.nameText, this.descText);
      this.onCreated(pack);
    } catch (error) {
      this.onMessage((error as Error).message);
    }
  }

  build() {
    Column() {
      Text('新建卡包')
        .fontSize(18)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .width('100%')

      Text('卡包名称')
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 12 })
      TextInput({ text: $$this.nameText, placeholder: '卡包名称' })
        .fontSize(14)
        .width('100%')
        .margin({ top: 4 })

      Text('说明（选填）')
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 12 })
      TextInput({ text: $$this.descText, placeholder: '说明（选填）' })
        .fontSize(14)
        .width('100%')
        .margin({ top: 4 })

      Row() {
        Text('取消')
          .fontSize(15)
          .fontColor(Ui.MUTED)
          .padding(12)
          .onClick(() => {
            this.onClose();
          })
        Blank()
        Text('创建')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding(12)
          .onClick(() => {
            this.confirmCreate();
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
