import { bundleManager } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { AppStore } from '../common/AppStore';
import { CardPack } from '../core/CardPack';
import { CardScript } from '../core/CardScript';
import { CardSchemes } from '../core/CardSchemes';
import { ScriptCodec } from '../core/ScriptCodec';
import { SimulatorStore } from '../data/SimulatorStore';
import { DocumentController } from '../io/DocumentController';
import { ImportEvidence } from '../importer/ImportEvidence';
import { CapturedTransaction } from '../importer/TransactionLogParser';
import { JsonSheetContent } from '../components/JsonSheetContent';
import { PackImportSheet } from '../components/PackImportSheet';
import { TransactionImportSheet } from '../components/TransactionImportSheet';
import { Ui } from '../components/Ui';

/** 首页：卡包标题、分类筛选、卡片列表与导入入口；详情等页面经系统路由表入栈。 */
@Entry
@Component
struct Index {
  @State packName: string = '';
  @State packBuiltIn: boolean = true;
  @State versionLine: string = 'NFC 卡模拟器';
  @State categories: string[] = ['全部'];
  @State pageIndex: number = 0;
  @State highlightedCategory: number = 0;
  @State cards: CardScript[] = [];
  @State showPackImport: boolean = false;
  @State showTransactionImport: boolean = false;
  @State showJson: boolean = false;
  @State jsonTitle: string = '';
  @State jsonText: string = '';
  pathStack: NavPathStack = new NavPathStack();
  appStore: AppStore = AppStore.getInstance();
  store: SimulatorStore = this.appStore.store;
  documents: DocumentController | null = null;
  importPackData: CardPack | null = null;
  importTransactions: CapturedTransaction[] | null = null;
  importEvidence: ImportEvidence | null = null;
  private swiperController: SwiperController = new SwiperController();
  private chipScroller: Scroller = new Scroller();
  private packId: string = '';
  private listener: () => void = (): void => {};

  aboutToAppear(): void {
    this.readVersion();
    this.documents = new DocumentController(getContext(this), this.store,
      (pack: CardPack): void => {
        this.importPackData = pack;
        this.showPackImport = true;
      },
      (transactions: CapturedTransaction[], evidence: ImportEvidence): void => {
        this.importTransactions = transactions;
        this.importEvidence = evidence;
        this.showTransactionImport = true;
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

  private readVersion(): void {
    try {
      const info = bundleManager.getBundleInfoForSelfSync(bundleManager.BundleFlag.GET_BUNDLE_INFO_DEFAULT);
      this.versionLine = info.versionName.length > 0 ? `NFC 卡模拟器 · v${info.versionName}` : 'NFC 卡模拟器';
    } catch (error) {
      this.versionLine = 'NFC 卡模拟器';
    }
  }

  /** 从仓库重建列表快照；切包时重置分类页到全部，保证筛选始终属于当前包。 */
  private refresh(): void {
    const pack = this.store.currentPack();
    this.packName = pack.name;
    this.packBuiltIn = pack.builtIn;
    if (pack.id !== this.packId) {
      this.packId = pack.id;
      this.pageIndex = 0;
      this.highlightedCategory = 0;
      this.chipScroller.scrollToIndex(0);
    }
    const schemes: string[] = [];
    for (const card of pack.cards) {
      schemes.push(card.scheme);
    }
    this.categories = CardSchemes.categories(schemes);
    if (this.pageIndex >= this.categories.length) {
      this.pageIndex = 0;
      this.highlightedCategory = 0;
      this.chipScroller.scrollToIndex(0);
    }
    this.cards = pack.cards.slice();
  }

  /** 取得指定分类页要展示的卡片；全部页返回当前包整表。 */
  private categoryCards(category: string): CardScript[] {
    const result: CardScript[] = [];
    for (const card of this.cards) {
      if (category === '全部' || card.scheme.toLowerCase() === category.toLowerCase()) {
        result.push(card);
      }
    }
    return result;
  }

  /** 点击分类条按索引切页，带滑动动画；与内容横滑共用 onChange 同步状态。 */
  private selectCategory(index: number): void {
    this.highlightedCategory = index;
    if (index !== this.pageIndex) {
      this.swiperController.changeIndex(index, true);
    }
  }

  private toast(text: string): void {
    this.getUIContext().getPromptAction().showToast({ message: text, duration: 2500 });
  }

  private openCard(card: CardScript): void {
    this.appStore.session.open(card.key);
    this.pathStack.pushPathByName('CardDetail', null);
  }

  private openEditorFromList(card: CardScript | null, copy: boolean): void {
    this.appStore.openEditor(card, copy, false);
    this.pathStack.pushPathByName('CardEditor', null);
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
      value: '新建卡片',
      action: (): void => {
        this.openEditorFromList(null, false);
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

  private settingsMenu(): MenuElement[] {
    const menus: MenuElement[] = [];
    menus.push({
      value: 'NFC 设置',
      action: (): void => {
        this.appStore.nfc.nfcSettings((text: string): void => {
          this.toast(text);
        });
      }
    });
    menus.push({
      value: '默认支付设置',
      action: (): void => {
        this.appStore.nfc.paymentSettings((text: string): void => {
          this.toast(text);
        });
      }
    });
    return menus;
  }

  private cardMenu(card: CardScript): MenuElement[] {
    const menus: MenuElement[] = [];
    menus.push({
      value: card.builtIn ? '复制后编辑' : '编辑卡片',
      action: (): void => {
        this.openEditorFromList(card, false);
      }
    });
    menus.push({
      value: '复制卡片',
      action: (): void => {
        this.openEditorFromList(card, true);
      }
    });
    menus.push({
      value: '查看 JSON',
      action: (): void => {
        this.jsonTitle = '脚本 JSON';
        this.jsonText = ScriptCodec.encode([card]);
        this.showJson = true;
      }
    });
    menus.push({
      value: '导出卡片',
      action: (): void => {
        const documents = this.documents;
        if (documents !== null) {
          documents.exportCard(card);
        }
      }
    });
    if (!card.builtIn) {
      menus.push({
        value: '删除卡片',
        action: (): void => {
          this.confirmDeleteCard(card);
        }
      });
    }
    return menus;
  }

  private confirmDeleteCard(card: CardScript): void {
    AlertDialog.show({
      title: '删除此卡片？',
      message: card.name,
      buttons: [
        { value: '取消', action: (): void => {} },
        {
          value: '删除',
          action: (): void => {
            this.deleteCard(card);
          }
        }
      ]
    });
  }

  private deleteCard(card: CardScript): void {
    try {
      this.store.delete(card.key);
      this.refresh();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  /** 交易导入转换为单卡新包；与 Android 一致，导入成功即切换并刷新列表。 */
  private saveTransactionCard(card: CardScript): void {
    try {
      const pack = new CardPack(`pack-${util.generateRandomUUID(false)}`, card.name, '', [card], false);
      this.store.importPack(pack, null);
      this.showTransactionImport = false;
      this.importTransactions = null;
      this.importEvidence = null;
      this.refresh();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  @Builder
  packImportSheetBuilder() {
    PackImportSheet({
      pack: this.importPackData,
      onImported: (pack: CardPack): void => {
        this.showPackImport = false;
        this.importPackData = null;
        this.refresh();
      },
      onMessage: (text: string): void => {
        this.toast(text);
      },
      onClose: (): void => {
        this.showPackImport = false;
        this.importPackData = null;
      }
    })
  }

  @Builder
  transactionImportSheetBuilder() {
    TransactionImportSheet({
      transactions: this.importTransactions,
      evidence: this.importEvidence,
      onSave: (card: CardScript): void => {
        this.saveTransactionCard(card);
      },
      onClose: (): void => {
        this.showTransactionImport = false;
        this.importTransactions = null;
        this.importEvidence = null;
      }
    })
  }

  @Builder
  jsonSheetBuilder() {
    JsonSheetContent({ titleText: this.jsonTitle, jsonText: this.jsonText })
  }

  /** 列表卡片面板：长名称换行完整显示，菜单与点击区域独立。 */
  @Builder
  cardRowBuilder(card: CardScript) {
    Column() {
      Row() {
        Text(card.name)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(Ui.INK)
          .layoutWeight(1)
        Image($r('app.media.ic_more'))
          .width(32)
          .height(32)
          .objectFit(ImageFit.Contain)
          .fillColor(Ui.INK)
          .padding(4)
          .accessibilityText('更多操作')
          .hitTestBehavior(HitTestMode.Block)
          .bindMenu(this.cardMenu(card))
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)
      Text(`${card.scheme} · ${card.rules.length} 条规则`)
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 4 })
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .margin({ top: 12 })
    .alignItems(HorizontalAlign.Start)
    .onClick(() => {
      this.openCard(card);
    })
  }

  /** 单个分类页：空包引导、空分类占位或该分类卡片列表，纵向滚动独立于横滑切页。 */
  @Builder
  categoryPageBuilder(category: string) {
    Scroll() {
      Column() {
        if (this.cards.length === 0) {
          if (!this.packBuiltIn) {
            Column() {
              Text('此卡包暂无卡片')
                .fontSize(16)
                .fontWeight(FontWeight.Bold)
                .fontColor(Ui.INK)
                .width('100%')
              Text('新建卡片')
                .fontSize(15)
                .fontColor(Ui.BLUE)
                .padding({ top: 12, bottom: 4 })
                .onClick(() => {
                  this.openEditorFromList(null, false);
                })
            }
            .width('100%')
            .padding(16)
            .backgroundColor(Ui.WHITE)
            .borderRadius(Ui.PANEL_RADIUS)
            .alignItems(HorizontalAlign.Start)
          } else {
            Text('暂无卡片，可新建或导入。')
              .fontSize(14)
              .fontColor(Ui.MUTED)
              .padding({ top: 24, bottom: 24 })
          }
        } else if (this.categoryCards(category).length === 0) {
          Text('此分类暂无卡片')
            .fontSize(14)
            .fontColor(Ui.MUTED)
            .padding({ top: 24, bottom: 24 })
        } else {
          ForEach(this.categoryCards(category), (card: CardScript) => {
            this.cardRowBuilder(card)
          }, (card: CardScript) => card.key)
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16, bottom: 24 })
    }
    .align(Alignment.TopStart)
    .width('100%')
    .height('100%')
  }

  build() {
    Navigation(this.pathStack) {
      Column() {
        Row() {
          Column() {
            Text(this.packName)
              .fontSize(18)
              .fontWeight(FontWeight.Bold)
              .fontColor(Color.White)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
            Text(this.versionLine)
              .fontSize(11)
              .fontColor(Ui.TOOLBAR_TEXT)
              .maxLines(1)
          }
          .alignItems(HorizontalAlign.Start)
          .layoutWeight(1)
          .onClick(() => {
            this.pathStack.pushPathByName('PackManager', null);
          })

          Image($r('app.media.ic_expand_more'))
            .width(28)
            .height(28)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(2)
            .accessibilityText('管理卡包')
            .rotate({ angle: -90 })
            .onClick(() => {
              this.pathStack.pushPathByName('PackManager', null);
            })
          Image($r('app.media.ic_add'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('添加')
            .bindMenu(this.addMenu())
          Image($r('app.media.ic_logs'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('全部日志')
            .onClick(() => {
              this.pathStack.pushPathByName('AllLogs', null);
            })
          Image($r('app.media.ic_settings'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('设置')
            .bindMenu(this.settingsMenu())
        }
        .width('100%')
        .height(56)
        .padding({ left: 16, right: 4 })
        .backgroundColor(Ui.INK)
        .alignItems(VerticalAlign.Center)

        List({ scroller: this.chipScroller, initialIndex: 0 }) {
          ForEach(this.categories, (label: string, index: number) => {
            ListItem() {
              Text(label)
                .fontSize(13)
                .fontColor(this.highlightedCategory === index ? Color.White : Ui.INK)
                .padding({ left: 14, right: 14, top: 6, bottom: 6 })
                .backgroundColor(this.highlightedCategory === index ? Ui.BLUE : Ui.WHITE)
                .borderRadius(16)
                .margin({ left: 4, right: 4 })
                .onClick(() => {
                  this.selectCategory(index);
                })
            }
          }, (label: string) => label)
        }
        .listDirection(Axis.Horizontal)
        .alignListItem(ListItemAlign.Center)
        .scrollBar(BarState.Off)
        .edgeEffect(EdgeEffect.Spring)
        .width('100%')
        .height(48)
        .padding({ left: 12, right: 12 })
        .bindSheet($$this.showTransactionImport, this.transactionImportSheetBuilder(), {
          height: SheetSize.LARGE,
          onDisappear: (): void => {
            this.showTransactionImport = false;
            this.importTransactions = null;
            this.importEvidence = null;
          }
        })

        Swiper(this.swiperController) {
          ForEach(this.categories, (label: string) => {
            this.categoryPageBuilder(label)
          }, (label: string) => label)
        }
        .index(this.pageIndex)
        .loop(false)
        .indicator(false)
        .duration(250)
        .layoutWeight(1)
        .width('100%')
        .onChange((index: number) => {
          this.pageIndex = index;
          this.highlightedCategory = index;
          // 已完整可见时保留位置，超出视口才平滑滚动，避免每次切页都强制贴左。
          this.chipScroller.scrollToIndex(index, true, ScrollAlign.AUTO);
        })
        .onContentDidScroll((selectedIndex: number, index: number, position: number, mainAxisLength: number) => {
          const nearest = Math.round(Math.max(0, Math.min(this.categories.length - 1, index - position)));
          if (nearest !== this.highlightedCategory) {
            this.highlightedCategory = nearest;
            this.chipScroller.scrollToIndex(nearest, true, ScrollAlign.AUTO);
          }
        })
        .bindSheet($$this.showJson, this.jsonSheetBuilder(), {
          height: SheetSize.LARGE,
          onDisappear: (): void => {
            this.showJson = false;
          }
        })
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Ui.PAGE_BG)
      .bindSheet($$this.showPackImport, this.packImportSheetBuilder(), {
        height: SheetSize.LARGE,
        onDisappear: (): void => {
          this.showPackImport = false;
          this.importPackData = null;
        }
      })
    }
    .mode(NavigationMode.Stack)
    .hideTitleBar(true)
  }
}
