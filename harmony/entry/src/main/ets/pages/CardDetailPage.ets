import { AppStore } from '../common/AppStore';
import { CardMetadata } from '../core/CardMetadata';
import { CardPack } from '../core/CardPack';
import { CardScript } from '../core/CardScript';
import { ScriptCodec } from '../core/ScriptCodec';
import { SimulatorStore } from '../data/SimulatorStore';
import { DocumentController } from '../io/DocumentController';
import { ImportEvidence } from '../importer/ImportEvidence';
import { CapturedTransaction } from '../importer/TransactionLogParser';
import { DebugPanel } from '../components/DebugPanel';
import { DescriptionText } from '../components/DescriptionText';
import { JsonSheetContent } from '../components/JsonSheetContent';
import { LogsPanel } from '../components/LogsPanel';
import { MatchingLabels } from '../components/MatchingLabels';
import { RuleList } from '../components/RuleList';
import { ScenarioPanel } from '../components/ScenarioPanel';
import { SimulationControl } from '../components/SimulationControl';
import { Ui } from '../components/Ui';

@Builder
export function CardDetailBuilder() {
  CardDetailPage()
}

/** 卡片详情：模拟开关、详情/调试/日志三页签与卡片操作菜单。 */
@Component
struct CardDetailPage {
  @State card: CardScript = new CardScript('', '', '其他', [], '', [], false, '6D00', false, new CardMetadata(), '');
  @State simEnabled: boolean = false;
  @State problem: string = '';
  @State simRevision: number = 0;
  @State selectedTab: number = 0;
  @State tabPosition: number = 0;
  @State showJson: boolean = false;
  @State jsonTitle: string = '';
  @State jsonText: string = '';
  pathStack: NavPathStack = new NavPathStack();
  appStore: AppStore = AppStore.getInstance();
  store: SimulatorStore = this.appStore.store;
  documents: DocumentController | null = null;
  private tabsController: TabsController = new TabsController();
  private tabLabels: string[] = ['详情', '调试', '日志'];
  private listener: () => void = (): void => {};

  aboutToAppear(): void {
    this.documents = new DocumentController(getContext(this), this.store,
      (pack: CardPack): void => {},
      (transactions: CapturedTransaction[], evidence: ImportEvidence): void => {},
      (): void => {},
      (text: string): void => {
        this.toast(text);
      });
    this.listener = (): void => {
      this.refresh();
    };
    this.store.addListener(this.listener);
  }

  aboutToDisappear(): void {
    this.store.removeListener(this.listener);
    this.appStore.session.leave();
  }

  /** 会话目标或选卡失效时退回列表；正常刷新同步模拟状态和错误。 */
  private refresh(): void {
    const id = this.appStore.session.cardId;
    if (id === null) {
      this.pathStack.pop();
      return;
    }
    const card = this.store.findCard(id);
    if (card === null) {
      this.pathStack.pop();
      return;
    }
    this.card = card;
    this.simEnabled = this.store.enabled;
    this.problem = this.appStore.session.error ?? '';
    this.simRevision++;
  }

  private toast(text: string): void {
    this.getUIContext().getPromptAction().showToast({ message: text, duration: 2500 });
  }

  private aidBlock(): string {
    return `AID\n${this.card.aids.join('\n')}`;
  }

  private editActionLabel(): string {
    return this.card.builtIn ? '复制后编辑' : '编辑卡片';
  }

  private openEditor(copy: boolean): void {
    this.appStore.openEditor(this.card, copy, true);
    this.pathStack.pushPathByName('CardEditor', null);
  }

  private exportLogs(): void {
    const documents = this.documents;
    if (documents !== null) {
      documents.exportLogs(this.card.key);
    }
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

  private cardMenu(): MenuElement[] {
    const card = this.card;
    const menus: MenuElement[] = [];
    menus.push({
      value: card.builtIn ? '复制后编辑' : '编辑卡片',
      action: (): void => {
        this.openEditor(card.builtIn);
      }
    });
    menus.push({
      value: '复制卡片',
      action: (): void => {
        this.openEditor(true);
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
          this.confirmDeleteCard();
        }
      });
    }
    return menus;
  }

  private confirmDeleteCard(): void {
    AlertDialog.show({
      title: '删除此卡片？',
      message: this.card.name,
      buttons: [
        { value: '取消', action: (): void => {} },
        {
          value: '删除',
          action: (): void => {
            this.deleteCard();
          }
        }
      ]
    });
  }

  private deleteCard(): void {
    try {
      this.store.delete(this.card.key);
      this.pathStack.pop();
    } catch (error) {
      this.toast((error as Error).message);
    }
  }

  @Builder
  jsonSheetBuilder() {
    JsonSheetContent({ titleText: this.jsonTitle, jsonText: this.jsonText })
  }

  /** 页签高亮取最近页，指示线按内容的实际位移连续移动。 */
  @Builder
  tabBarBuilder() {
    Row() {
      Column() {
        Row() {
          ForEach(this.tabLabels, (label: string, index: number) => {
            Text(label)
              .fontSize(15)
              .fontWeight(this.selectedTab === index ? FontWeight.Bold : FontWeight.Normal)
              .fontColor(this.selectedTab === index ? Ui.INK : Ui.MUTED)
              .layoutWeight(1)
              .height(44)
              .textAlign(TextAlign.Center)
              .onClick(() => {
                this.selectTab(index);
              })
          }, (label: string) => label)
        }
        .width('100%')
        Stack() {
          Row()
            .width('30%')
            .height(3)
            .backgroundColor(Ui.BLUE)
            .borderRadius(1.5)
            .position({ x: `${this.tabPosition * 33.333 + 1.667}%`, y: 0 })
        }
        .width('100%')
        .height(3)
      }
      .width('100%')
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 12, bottom: 12 })
  }

  /** 点击立即反馈标签选择，内容与指示线的动画交给实际滚动回调同步。 */
  private selectTab(index: number): void {
    this.selectedTab = index;
    this.tabsController.changeIndex(index);
  }

  @Builder
  infoPanelBuilder() {
    Column() {
      Text(this.card.scheme)
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
      Text(this.aidBlock())
        .fontSize(13)
        .fontFamily(Ui.MONO)
        .fontColor(Ui.INK)
        .copyOption(CopyOptions.LocalDevice)
        .width('100%')
        .margin({ top: 8 })
        .textAlign(TextAlign.Start)
      if (this.card.description.length > 0) {
        DescriptionText({ bodyText: this.card.description })
          .margin({ top: 8 })
      }
      Text(this.editActionLabel())
        .fontSize(15)
        .fontColor(Ui.BLUE)
        .padding({ top: 12, bottom: 4 })
        .onClick(() => {
          this.openEditor(this.card.builtIn);
        })
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  rulesPanelBuilder() {
    Column() {
      Row() {
        Text(`APDU 规则 · ${this.card.rules.length} 条`)
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(Ui.INK)
          .layoutWeight(1)
        Image($r('app.media.ic_info'))
          .width(24)
          .height(24)
          .objectFit(ImageFit.Contain)
          .fillColor(Ui.MUTED)
          .accessibilityText('查看说明')
          .onClick(() => {
            MatchingLabels.showExecutionHelp();
          })
      }
      .width('100%')
      Text(`${MatchingLabels.execution(this.card.ordered)} · 未匹配时返回 ${this.card.fallback}`)
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 4 })
      RuleList({ rules: this.card.rules })
        .margin({ top: 4 })
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .margin({ top: 12 })
    .alignItems(HorizontalAlign.Start)
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
          Text(this.card.name)
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Image($r('app.media.ic_settings'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('设置')
            .bindMenu(this.settingsMenu())
          Image($r('app.media.ic_more'))
            .width(40)
            .height(40)
            .objectFit(ImageFit.Contain)
            .fillColor(Color.White)
            .padding(8)
            .accessibilityText('更多操作')
            .bindMenu(this.cardMenu())
        }
        .width('100%')
        .height(56)
        .padding({ left: 4, right: 4 })
        .backgroundColor(Ui.INK)
        .alignItems(VerticalAlign.Center)

        SimulationControl({
          simEnabled: this.simEnabled,
          problem: this.problem,
          revision: this.simRevision,
          onSwitch: (value: boolean): void => {
            this.appStore.session.setRequested(value);
            this.refresh();
          }
        })
        .margin({ left: 16, right: 16, top: 12 })

        this.tabBarBuilder()

        Tabs({ barPosition: BarPosition.Start, controller: this.tabsController }) {
          TabContent() {
            Scroll() {
              Column() {
                this.infoPanelBuilder()
                ScenarioPanel({ card: this.card })
                this.rulesPanelBuilder()
              }
              .width('100%')
              .padding({ left: 16, right: 16, bottom: 24 })
            }
            .align(Alignment.TopStart)
            .width('100%')
            .height('100%')
          }
          TabContent() {
            Scroll() {
              Column() {
                DebugPanel({
                  message: (text: string): void => {
                    this.toast(text);
                  }
                })
              }
              .width('100%')
              .padding({ left: 16, right: 16, bottom: 24 })
            }
            .align(Alignment.TopStart)
            .width('100%')
            .height('100%')
          }
          TabContent() {
            LogsPanel({
              filterCardId: this.card.key,
              onExport: (): void => {
                this.exportLogs();
              }
            })
            .padding({ left: 16, right: 16, bottom: 16 })
          }
        }
        .barHeight(0)
        .animationDuration(300)
        .layoutWeight(1)
        .width('100%')
        .onContentDidScroll((selectedIndex: number, index: number, position: number, mainAxisLength: number) => {
          // position 是页宽的比例；任一可见页的 index - position 都对应同一连续页码。
          this.tabPosition = Math.max(0, Math.min(this.tabLabels.length - 1, index - position));
          this.selectedTab = Math.round(this.tabPosition);
        })
        .onAnimationEnd((index: number, event: TabsAnimationEvent) => {
          this.tabPosition = index;
          this.selectedTab = index;
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
      this.appStore.setDetailVisible(true);
      this.refresh();
    })
    .onHidden(() => {
      this.appStore.setDetailVisible(false);
    })
  }
}
