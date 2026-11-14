import { util } from '@kit.ArkTS';
import { AppStore } from '../common/AppStore';
import { CardPack, CardPacks } from '../core/CardPack';
import { SimulatorStore } from '../data/SimulatorStore';
import { Ui } from './Ui';

/** 文件导入总是先选择目标再预览；同编号只提示更新，不根据 ID 静默覆盖。 */
@Component
export struct PackImportSheet {
  pack: CardPack | null = null;
  store: SimulatorStore = AppStore.getInstance().store;
  onImported: (pack: CardPack) => void = () => {};
  onMessage: (text: string) => void = () => {};
  onClose: () => void = () => {};
  @State step: number = 1;
  @State target: number = 0;
  @State choiceLabels: string[] = [];
  @State nameText: string = '';
  private incoming: CardPack | null = null;
  private existing: CardPack[] = [];

  aboutToAppear(): void {
    const incoming = this.pack;
    if (incoming === null) {
      return;
    }
    this.incoming = incoming;
    const existing: CardPack[] = [];
    for (const item of this.store.packs()) {
      if (!item.builtIn && item.id !== CardPacks.PERSONAL) {
        existing.push(item);
      }
    }
    this.existing = existing;
    const labels: string[] = ['新建卡包'];
    let matched = -1;
    for (let index = 0; index < existing.length; index++) {
      labels.push(`更新：${existing[index].name}`);
      if (existing[index].id === incoming.id) {
        matched = index;
      }
    }
    this.choiceLabels = labels;
    this.target = matched >= 0 ? matched + 1 : 0;
    this.step = 1;
  }

  private stepTitle(): string {
    if (this.step === 1) {
      const incoming = this.incoming;
      const count = incoming === null ? 0 : incoming.cards.length;
      return `导入卡包 · ${count} 张`;
    }
    const old = this.target > 0 ? this.existing[this.target - 1] : null;
    return old === null ? '新建卡包' : `更新：${old.name}`;
  }

  private changeText(): string {
    const incoming = this.incoming;
    const old = this.target > 0 ? this.existing[this.target - 1] : null;
    if (old === null || incoming === null) {
      return '建立独立卡包，不影响已有卡片。';
    }
    const newIds: Set<string> = new Set<string>();
    for (const card of incoming.cards) {
      newIds.add(card.id);
    }
    const oldIds: Set<string> = new Set<string>();
    for (const card of old.cards) {
      oldIds.add(card.id);
    }
    let added = 0;
    let kept = 0;
    let removed = 0;
    for (const id of newIds) {
      if (oldIds.has(id)) {
        kept++;
      } else {
        added++;
      }
    }
    for (const id of oldIds) {
      if (!newIds.has(id)) {
        removed++;
      }
    }
    return `整包替换：新增 ${added}，更新 ${kept}，移除 ${removed} 张。`;
  }

  private cardNamesText(): string {
    const incoming = this.incoming;
    if (incoming === null) {
      return '';
    }
    const names: string[] = [];
    for (const card of incoming.cards) {
      names.push(card.name);
    }
    return names.join('\n').substring(0, 1600);
  }

  private warningsText(): string {
    const incoming = this.incoming;
    if (incoming === null) {
      return '';
    }
    const seen: Set<string> = new Set<string>();
    const warnings: string[] = [];
    for (const card of incoming.cards) {
      for (const warning of card.metadata.warnings) {
        if (!seen.has(warning)) {
          seen.add(warning);
          warnings.push(warning);
        }
      }
    }
    return warnings.join('\n').substring(0, 2000);
  }

  private toPreview(): void {
    const incoming = this.incoming;
    this.nameText = incoming === null ? '' : incoming.name;
    this.step = 2;
  }

  /** 新建时遇到已有包 ID 生成新 ID；更新时明确使用选定目标，失败不关闭表单。 */
  private confirmImport(): void {
    const incoming = this.incoming;
    if (incoming === null) {
      this.onClose();
      return;
    }
    try {
      const old = this.target > 0 ? this.existing[this.target - 1] : null;
      let id = incoming.id;
      if (old === null) {
        for (const item of this.store.packs()) {
          if (item.id === id) {
            id = `pack-${util.generateRandomUUID(false)}`;
            break;
          }
        }
      }
      const value = CardPacks.validateName(this.nameText);
      const candidate = CardPack.clone(incoming);
      candidate.id = id;
      candidate.name = value;
      const saved = this.store.importPack(candidate, old === null ? null : old.id);
      this.onImported(saved);
    } catch (error) {
      this.onMessage((error as Error).message);
    }
  }

  build() {
    Column() {
      Text(this.stepTitle())
        .fontSize(18)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .width('100%')

      if (this.step === 1) {
        Scroll() {
          Column() {
            ForEach(this.choiceLabels, (label: string, index: number) => {
              Row() {
                Radio({ value: `${index}`, group: 'packImportTarget' })
                  .checked(this.target === index)
                  .onChange((isChecked: boolean) => {
                    if (isChecked) {
                      this.target = index;
                    }
                  })
                Text(label)
                  .fontSize(15)
                  .fontColor(Ui.INK)
                  .layoutWeight(1)
                  .maxLines(1)
                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                  .padding({ left: 8 })
              }
              .width('100%')
              .height(48)
              .alignItems(VerticalAlign.Center)
            }, (label: string, index: number) => `${index}-${label}`)
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .align(Alignment.TopStart)
        .layoutWeight(1)
        .width('100%')
        .margin({ top: 4 })
      } else {
        Scroll() {
          Column() {
            TextInput({ text: $$this.nameText, placeholder: '卡包名称' })
              .fontSize(14)
              .width('100%')
            Text(this.changeText())
              .fontSize(14)
              .fontColor(Ui.INK)
              .width('100%')
              .margin({ top: 12 })
            Text(this.cardNamesText())
              .fontSize(13)
              .fontColor(Ui.INK)
              .copyOption(CopyOptions.LocalDevice)
              .width('100%')
              .margin({ top: 8 })
              .textAlign(TextAlign.Start)
            if (this.warningsText().length > 0) {
              Text(this.warningsText())
                .fontSize(12)
                .fontColor(Ui.MUTED)
                .copyOption(CopyOptions.LocalDevice)
                .width('100%')
                .margin({ top: 8 })
                .textAlign(TextAlign.Start)
            }
          }
          .width('100%')
          .alignItems(HorizontalAlign.Start)
        }
        .align(Alignment.TopStart)
        .layoutWeight(1)
        .width('100%')
        .margin({ top: 12 })
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
        if (this.step === 1) {
          Text('下一步')
            .fontSize(15)
            .fontColor(Ui.BLUE)
            .padding(12)
            .onClick(() => {
              this.toPreview();
            })
        } else {
          Text('确认导入')
            .fontSize(15)
            .fontColor(Ui.BLUE)
            .padding(12)
            .onClick(() => {
              this.confirmImport();
            })
        }
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
