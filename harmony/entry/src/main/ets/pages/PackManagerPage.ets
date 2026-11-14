import { util } from '@kit.ArkTS';
import { AppStore } from '../common/AppStore';
import { CardPack, CardPacks } from '../core/CardPack';
import { CardPackCodec } from '../core/CardPackCodec';
import { CardScript } from '../core/CardScript';
import { SimulatorStore } from '../data/SimulatorStore';
import { DocumentController } from '../io/DocumentController';
import { ImportEvidence } from '../importer/ImportEvidence';
import { CapturedTransaction } from '../importer/TransactionLogParser';
import { JsonSheetContent } from '../components/JsonSheetContent';
import { PackCreateSheet } from '../components/PackCreateSheet';
import { PackImportSheet } from '../components/PackImportSheet';
import { PackRenameSheet } from '../components/PackRenameSheet';
import { TransactionImportSheet } from '../components/TransactionImportSheet';
import { Ui } from '../components/Ui';

@Builder
export function PackManagerBuilder() {
  PackManagerPage()
}

/** 卡包管理：分组列表、切包、重命名/删除与文件导入导出。 */
@Component
struct PackManagerPage {
  @State builtinPacks: CardPack[] = [];
  @State userPacks: CardPack[] = [];
  @State activeId: string = '';
  @State showCreate: boolean = false;
  @State showImport: boolean = false;
  @State showRename: boolean = false;
  @State showJson: boolean = false;
  @State jsonTitle: string = '';
  @State jsonText: string = '';
  @State importPackData: CardPack | null = null;
  @State importTransactions: CapturedTransaction[] | null = null;
  @State importEvidence: ImportEvidence | null = null;
  pathStack: NavPathStack = new NavPathStack();
  appStore: AppStore = AppStore.getInstance();
  store: SimulatorStore = this.appStore.store;
  documents: DocumentController | null = null;
  renamePackData: CardPack | null = null;
  private listener: () => void = (): void => {};

  aboutToAppear(): void {
    this.documents = new DocumentController(getContext(this), this.store,
      (pack: CardPack): void => {
        this.importPackData = pack;
        this.importTransactions = null;
        this.importEvidence = null;
        this.showImport = true;
      },
      (transactions: CapturedTransaction[], evidence: ImportEvidence): void => {
        this.importTransactions = transactions;
        this.importEvidence = evidence;
        this.importPackData = null;
        this.showImport = true;
      },
      (): void => {
        this.refresh();
      },
      (text: string): void => {
        this.toast(text);
      });
    this.listener = (): void => {
      this.refresh();
    };
    this.store.addListener(this.listener);
    this.refresh();
  }

  aboutToDisappear(): void {
    this.store.removeListener(this.listener);
  }

  private refresh(): void {
    const builtin: CardPack[] = [];
    const user: CardPack[] = [];
    for (const pack of this.store.packs()) {
      if (pack.builtIn) {
        builtin.push(pack);
      } else {
        user.push(pack);
      }
    }
    this.builtinPacks = builtin;
    this.userPacks = user;
    this.activeId = this.store.activePackId;
  }

  private toast(text: string): void {
    this.getUIContext().getPromptAction().showToast({ message: text, duration: 2500 });
  }

  private usePack(pack: CardPack): void {
    try {
      this.store.switchPack(pack.id);
      this.pathStack.pop();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  private startImport(): void {
    const documents = this.documents;
    if (documents !== null) {
      documents.import();
    }
  }

  private addMenu(): MenuElement[] {
    const menus: MenuElement[] = [];
    menus.push({
      value: '新建卡包',
      action: (): void => {
        this.showCreate = true;
      }
    });
    menus.push({
      value: '导入文件',
      action: (): void => {
        this.startImport();
      }
    });
    return menus;
  }

  private packMenu(pack: CardPack): MenuElement[] {
    const menus: MenuElement[] = [];
    menus.push({
      value: '查看 JSON',
      action: (): void => {
        this.jsonTitle = pack.name;
        this.jsonText = CardPackCodec.encode(pack);
        this.showJson = true;
      }
    });
    menus.push({
      value: '导出卡包',
      action: (): void => {
        const documents = this.documents;
        if (documents !== null) {
          documents.exportPack(pack);
        }
      }
    });
    if (!pack.builtIn) {
      menus.push({
        value: '重命名',
        action: (): void => {
          this.renamePackData = pack;
          this.showRename = true;
        }
      });
    }
    if (!pack.builtIn && pack.id !== CardPacks.PERSONAL) {
      menus.push({
        value: '删除卡包',
        action: (): void => {
          this.confirmDeletePack(pack);
        }
      });
    }
    return menus;
  }

  private confirmDeletePack(pack: CardPack): void {
    AlertDialog.show({
      title: '删除整个卡包？',
      message: `${pack.name} · ${pack.cards.length} 张卡片；已保存的日志保留。`,
      buttons: [
        { value: '取消', action: (): void => {} },
        {
          value: '删除',
          action: (): void => {
            this.deletePack(pack);
          }
        }
      ]
    });
  }

  private deletePack(pack: CardPack): void {
    try {
      this.store.deletePack(pack.id);
      this.refresh();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  /** 交易文件同样转为单卡新包；导入成功后返回首页列表。 */
  private saveTransactionCard(card: CardScript): void {
    try {
      const pack = new CardPack(`pack-${util.generateRandomUUID(false)}`, card.name, '', [card], false);
      this.store.importPack(pack, null);
      this.closeImportSheet();
      this.pathStack.pop();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  private closeImportSheet(): void {
    this.showImport = false;
    this.importPackData = null;
    this.importTransactions = null;
    this.importEvidence = null;
  }

  @Builder
  packRowBuilder(pack: CardPack) {
    Row() {
      Column() {
        Row() {
          Text(pack.name)
            .fontSize(17)
            .fontWeight(FontWeight.Bold)
            .fontColor(Ui.INK)
            .layoutWeight(1)
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (pack.id === this.activeId) {
            Text('当前使用')
              .fontSize(11)
              .fontColor(Ui.BLUE)
              .backgroundColor('#EFF6FF')
              .padding({ left: 6, right: 6, top: 2, bottom: 2 })
              .borderRadius(8)
          }
        }
        .width('100%')
        Text(`${pack.cards.length} 张卡片`)
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 4 })
        if (pack.description.length > 0) {
          Text(pack.description)
            .fontSize(13)
            .fontColor(Ui.MUTED)
            .width('100%')
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
            .margin({ top: 4 })
        }
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)

      Image($r('app.media.ic_more'))
        .width(32)
        .height(32)
        .objectFit(ImageFit.Contain)
        .fillColor(Ui.INK)
        .padding(4)
        .accessibilityText('更多操作')
        .hitTestBehavior(HitTestMode.Block)
        .bindMenu(this.packMenu(pack))
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .margin({ top: 12 })
    .alignItems(VerticalAlign.Center)
    .onClick(() => {
      this.usePack(pack);
    })
  }

  @Builder
  createSheetBuilder() {
    PackCreateSheet({
      onCreated: (pack: CardPack): void => {
        this.showCreate = false;
        this.pathStack.pop();
      },
      onMessage: (text: string): void => {
        this.toast(text);
      },
      onClose: (): void => {
        this.showCreate = false;
      }
    })
  }

  @Builder
  importSheetBuilder() {
    if (this.importPackData !== null) {
      PackImportSheet({
        pack: this.importPackData,
        onImported: (pack: CardPack): void => {
          this.closeImportSheet();
          this.pathStack.pop();
        },
        onMessage: (text: string): void => {
          this.toast(text);
        },
        onClose: (): void => {
          this.closeImportSheet();
        }
      })
    } else {
      TransactionImportSheet({
        transactions: this.importTransactions,
        evidence: this.importEvidence,
        onSave: (card: CardScript): void => {
          this.saveTransactionCard(card);
        },
        onClose: (): void => {
          this.closeImportSheet();
        }
      })
    }
  }

  @Builder
  renameSheetBuilder() {
    PackRenameSheet({
      pack: this.renamePackData,
      onRenamed: (): void => {
        this.showRename = false;
        this.renamePackData = null;
        this.refresh();
      },
      onMessage: (text: string): void => {
        this.toast(text);
      },
      onClose: (): void => {
        this.showRename = false;
        this.renamePackData = null;
      }
    })
  }

  @Builder
  jsonSheetBuilder() {
    JsonSheetContent({ titleText: this.jsonTitle, jsonText: this.jsonText })
  }

  build() {
    NavDestination() {
      Column() {
        Row() {
          Image($r('app.media.ic_back'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('返回')
            .onClick(() => {
              this.pathStack.pop();
            })
          Text('管理卡包')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .layoutWeight(1)
            .maxLines(1)
          Image($r('app.media.ic_add'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('添加')
            .bindMenu(this.addMenu())
            .bindSheet($$this.showCreate, this.createSheetBuilder(), {
              height: SheetSize.FIT_CONTENT,
              onDisappear: (): void => {
                this.showCreate = false;
              }
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: 4, right: 4 })
        .backgroundColor(Ui.INK)
        .alignItems(VerticalAlign.Center)
        .bindSheet($$this.showImport, this.importSheetBuilder(), {
          height: SheetSize.LARGE,
          onDisappear: (): void => {
            this.closeImportSheet();
          }
        })

        Scroll() {
          Column() {
            Text('内置卡包')
              .fontSize(13)
              .fontWeight(FontWeight.Bold)
              .fontColor(Ui.MUTED)
              .width('100%')
              .margin({ top: 12 })
            ForEach(this.builtinPacks, (pack: CardPack) => {
              this.packRowBuilder(pack)
            }, (pack: CardPack) => pack.id)

            Text('我的卡包')
              .fontSize(13)
              .fontWeight(FontWeight.Bold)
              .fontColor(Ui.MUTED)
              .width('100%')
              .margin({ top: 20 })
            ForEach(this.userPacks, (pack: CardPack) => {
              this.packRowBuilder(pack)
            }, (pack: CardPack) => `${pack.id}-${pack.name}`)

            Text('新建卡包')
              .fontSize(15)
              .fontColor(Ui.BLUE)
              .padding({ top: 16, bottom: 16 })
              .onClick(() => {
                this.showCreate = true;
              })
          }
          .width('100%')
          .padding({ left: 16, right: 16, bottom: 24 })
        }
        .align(Alignment.TopStart)
        .layoutWeight(1)
        .width('100%')
        .bindSheet($$this.showRename, this.renameSheetBuilder(), {
          height: SheetSize.FIT_CONTENT,
          onDisappear: (): void => {
            this.showRename = false;
            this.renamePackData = null;
          }
        })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Ui.PAGE_BG)
      .bindSheet($$this.showJson, this.jsonSheetBuilder(), {
        height: SheetSize.LARGE,
        onDisappear: (): void => {
          this.showJson = false;
        }
      })
    }
    .hideTitleBar(true)
    .onReady((ctx: NavDestinationContext) => {
      this.pathStack = ctx.pathStack;
    })
    .onShown(() => {
      this.refresh();
    })
  }
}
