import { AppStore } from '../common/AppStore';
import { CardPack } from '../core/CardPack';
import { SimulatorStore } from '../data/SimulatorStore';
import { DocumentController } from '../io/DocumentController';
import { ImportEvidence } from '../importer/ImportEvidence';
import { CapturedTransaction } from '../importer/TransactionLogParser';
import { LogsPanel } from '../components/LogsPanel';
import { Ui } from '../components/Ui';

@Builder
export function AllLogsBuilder() {
  AllLogsPage()
}

/** 全部日志页：与详情日志共用面板，仅过滤范围不同。 */
@Component
struct AllLogsPage {
  pathStack: NavPathStack = new NavPathStack();
  appStore: AppStore = AppStore.getInstance();
  store: SimulatorStore = this.appStore.store;
  documents: DocumentController | null = null;

  aboutToAppear(): void {
    this.documents = new DocumentController(getContext(this), this.store,
      (pack: CardPack): void => {},
      (transactions: CapturedTransaction[], evidence: ImportEvidence): void => {},
      (): void => {},
      (text: string): void => {
        this.toast(text);
      });
  }

  private toast(text: string): void {
    this.getUIContext().getPromptAction().showToast({ message: text, duration: 2500 });
  }

  private exportLogs(): void {
    const documents = this.documents;
    if (documents !== null) {
      documents.exportLogs(null);
    }
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
          Text('全部日志')
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .layoutWeight(1)
            .maxLines(1)
        }
        .width('100%')
        .height(56)
        .padding({ left: 4, right: 16 })
        .backgroundColor(Ui.INK)
        .alignItems(VerticalAlign.Center)

        LogsPanel({
          filterCardId: '',
          onExport: (): void => {
            this.exportLogs();
          }
        })
          .padding({ left: 16, right: 16, bottom: 16 })
          .layoutWeight(1)
          .width('100%')
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Ui.PAGE_BG)
    }
    .hideTitleBar(true)
    .onReady((ctx: NavDestinationContext) => {
      this.pathStack = ctx.pathStack;
    })
  }
}
