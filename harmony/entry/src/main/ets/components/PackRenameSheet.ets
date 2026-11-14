import { AppStore } from '../common/AppStore';
import { CardPack } from '../core/CardPack';
import { SimulatorStore } from '../data/SimulatorStore';
import { Ui } from './Ui';

/** 仅修改用户包名称；保留 ID 和卡片，校验失败不关闭表单。 */
@Component
export struct PackRenameSheet {
  pack: CardPack | null = null;
  store: SimulatorStore = AppStore.getInstance().store;
  onRenamed: () => void = () => {};
  onMessage: (text: string) => void = () => {};
  onClose: () => void = () => {};
  @State nameText: string = '';

  aboutToAppear(): void {
    const pack = this.pack;
    if (pack !== null) {
      this.nameText = pack.name;
    }
  }

  private confirmRename(): void {
    const pack = this.pack;
    if (pack === null) {
      this.onClose();
      return;
    }
    try {
      this.store.renamePack(pack.id, this.nameText);
      this.onRenamed();
    } catch (error) {
      this.onMessage((error as Error).message);
    }
  }

  build() {
    Column() {
      Text('重命名卡包')
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

      Row() {
        Text('取消')
          .fontSize(15)
          .fontColor(Ui.MUTED)
          .padding(12)
          .onClick(() => {
            this.onClose();
          })
        Blank()
        Text('保存')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding(12)
          .onClick(() => {
            this.confirmRename();
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
