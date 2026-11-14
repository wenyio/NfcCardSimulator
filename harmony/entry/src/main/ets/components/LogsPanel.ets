import { AppStore } from '../common/AppStore';
import { LogEntry } from '../data/LogRepository';
import { SimulatorStore } from '../data/SimulatorStore';
import { Ui } from './Ui';

function pad(value: number, width: number): string {
  let text = `${value}`;
  while (text.length < width) {
    text = `0${text}`;
  }
  return text;
}

function formatTime(time: number): string {
  const date = new Date(time);
  return `${pad(date.getMonth() + 1, 2)}-${pad(date.getDate(), 2)} ` +
    `${pad(date.getHours(), 2)}:${pad(date.getMinutes(), 2)}:` +
    `${pad(date.getSeconds(), 2)}.${pad(date.getMilliseconds(), 3)}`;
}

/** 会话分组仅用于展示，导出由页面触发；清空立即生效且不可恢复。 */
@Component
export struct LogsPanel {
  @Prop filterCardId: string = '';
  onExport: () => void = () => {};
  store: SimulatorStore = AppStore.getInstance().store;
  @State logText: string = '';
  private listener: () => void = () => {};
  private refreshTimer: number = -1;
  private scroller: Scroller = new Scroller();

  aboutToAppear(): void {
    this.listener = (): void => {
      this.scheduleRefresh();
    };
    this.store.addListener(this.listener);
    this.refresh();
  }

  aboutToDisappear(): void {
    this.store.removeListener(this.listener);
    if (this.refreshTimer !== -1) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = -1;
    }
  }

  private scheduleRefresh(): void {
    if (this.refreshTimer === -1) {
      this.refreshTimer = setTimeout(() => {
        this.refreshTimer = -1;
        this.refresh();
      }, 150);
    }
  }

  private clearLabel(): string {
    return this.filterCardId.length > 0 ? '清空本卡日志' : '清空全部日志';
  }

  private refresh(): void {
    const entries: LogEntry[] = this.store.logs();
    const parts: string[] = [];
    let previous = '';
    for (const entry of entries) {
      if (this.filterCardId.length > 0 && entry.cardId !== this.filterCardId) {
        continue;
      }
      const group = entry.session !== previous ? `━━ ${entry.session} ━━\n` : '';
      previous = entry.session;
      parts.push(`${group}${formatTime(entry.time)}  ${entry.kind}\n${entry.message}`);
    }
    this.logText = parts.length > 0 ? parts.join('\n\n') :
      '暂无记录。NFC 收发和手动调试结果将显示在这里。';
    setTimeout(() => {
      this.scroller.scrollEdge(Edge.Bottom);
    }, 50);
  }

  build() {
    Column() {
      Row({ space: 16 }) {
        Text('导出日志')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding({ top: 8, bottom: 8 })
          .onClick(() => {
            this.onExport();
          })
        Text(this.clearLabel())
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding({ top: 8, bottom: 8 })
          .onClick(() => {
            this.store.clearLogs(this.filterCardId.length > 0 ? this.filterCardId : null);
            this.refresh();
          })
      }
      .width('100%')

      Text('最近记录按会话分组，自动保存到本机。')
        .fontSize(12)
        .fontColor(Ui.MUTED)
        .width('100%')

      Scroll(this.scroller) {
        Text(this.logText)
          .fontSize(12)
          .fontFamily(Ui.MONO)
          .fontColor(Ui.INK)
          .copyOption(CopyOptions.LocalDevice)
          .padding(12)
          .width('100%')
          .textAlign(TextAlign.Start)
      }
      .align(Alignment.TopStart)
      .layoutWeight(1)
      .width('100%')
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ top: 8 })
    }
    .width('100%')
    .height('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
