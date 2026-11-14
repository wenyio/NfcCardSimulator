import { Ui } from './Ui';

/** 详情页的模拟开关卡片；显示实际启用状态，用户切换才回调，程序同步不触发业务。 */
@Component
export struct SimulationControl {
  @Prop simEnabled: boolean = false;
  @Prop problem: string = '';
  @Prop @Watch('syncFromParent') revision: number = 0;
  @State switchOn: boolean = false;
  onSwitch: (value: boolean) => void = () => {};

  aboutToAppear(): void {
    this.switchOn = this.simEnabled;
  }

  syncFromParent(): void {
    this.switchOn = this.simEnabled;
  }

  build() {
    Row() {
      if (this.simEnabled) {
        Circle({ width: 10, height: 10 }).fill(Ui.GREEN)
      } else {
        Circle({ width: 10, height: 10 }).fill(Color.Transparent).stroke(Ui.MUTED).strokeWidth(1)
      }
      Text('卡片模拟')
        .fontSize(16)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .padding({ left: 10, right: 8 })
      if (!this.simEnabled && this.problem.length > 0) {
        Image($r('app.media.ic_error_outline'))
          .width(24)
          .height(24)
          .objectFit(ImageFit.Contain)
          .fillColor(Ui.WARN)
          .accessibilityText('模拟未启动原因')
          .onClick(() => {
            AlertDialog.show({
              title: '模拟未启动',
              message: this.problem,
              buttons: [{ value: '知道了', action: (): void => {} }]
            });
          })
      }
      Blank()
      Toggle({ type: ToggleType.Switch, isOn: $$this.switchOn })
        .onChange((isOn: boolean) => {
          if (isOn !== this.simEnabled) {
            this.onSwitch(isOn);
          }
        })
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .alignItems(VerticalAlign.Center)
  }
}
