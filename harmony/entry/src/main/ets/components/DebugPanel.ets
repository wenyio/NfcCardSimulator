import { AppStore } from '../common/AppStore';
import { ApduRule } from '../core/CardScript';
import { Hex } from '../core/Hex';
import { ScriptEngine } from '../core/ScriptEngine';
import { SimulatorStore } from '../data/SimulatorStore';
import { TlvDecoder } from '../diagnostics/TlvDecoder';
import { DebugCommandLabel } from './DebugCommandLabel';
import { Ui } from './Ui';

/** 手动引擎与射频引擎相互独立；切卡或修改脚本会使旧会话失效。 */
@Component
export struct DebugPanel {
  store: SimulatorStore = AppStore.getInstance().store;
  message: (text: string) => void = () => {};
  @State inputText: string = '';
  @State resultText: string = '等待输入命令';
  @State tlvText: string = '';
  @State commandLabels: string[] = [];
  @State selectedCommand: number = 0;
  private rules: ApduRule[] = [];
  private engine: ScriptEngine | null = null;
  private manualSession: string = '手动';
  private engineRevision: number = -1;
  private lastCardKey: string = '';
  private rulesSignature: string = '';
  private listener: () => void = () => {};

  aboutToAppear(): void {
    this.listener = (): void => {
      this.syncRules();
    };
    this.store.addListener(this.listener);
    this.syncRules();
  }

  aboutToDisappear(): void {
    this.store.removeListener(this.listener);
  }

  /** 选择器只填充输入框；卡切换或规则变化时重建选项，日志写入不重置用户输入。 */
  private syncRules(): void {
    const card = this.store.selected();
    const key = card === null ? '' : card.key;
    const rules = card === null ? [] : card.rules;
    let signature = '';
    for (const rule of rules) {
      signature += `${rule.command}|${rule.response}|${rule.mode}|${rule.label}|`;
    }
    if (key === this.lastCardKey && signature === this.rulesSignature) {
      return;
    }
    this.lastCardKey = key;
    this.rulesSignature = signature;
    this.rules = rules;
    const labels: string[] = [];
    for (let index = 0; index < rules.length; index++) {
      labels.push(DebugCommandLabel.format(index, rules[index]));
    }
    if (labels.length === 0) {
      labels.push('当前卡片暂无 APDU 规则');
    }
    this.commandLabels = labels;
    this.selectedCommand = 0;
    this.inputText = rules.length > 0 ? rules[0].command : '';
  }

  private commandOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    for (const label of this.commandLabels) {
      options.push({ value: label });
    }
    return options;
  }

  private selectedLabelText(): string {
    if (this.selectedCommand < this.commandLabels.length) {
      return this.commandLabels[this.selectedCommand];
    }
    return '';
  }

  /** 与 HCE 相同的脚本失效判断，但使用独立实例，手动发送不推进射频步骤。 */
  private send(): void {
    const card = this.store.selected();
    if (card === null) {
      this.message('请先选择卡片');
      return;
    }
    let engine = this.engine;
    if (engine === null || engine.script !== card || this.engineRevision !== this.store.sessionRevision) {
      engine = new ScriptEngine(card);
      this.engineRevision = this.store.sessionRevision;
      this.manualSession = this.store.beginSession('手动', card.name);
      this.engine = engine;
    }
    const exchange = engine.exchange(this.inputText);
    this.resultText = `R-APDU  ${exchange.response}\n${exchange.rule}\n${exchange.difference}`;
    this.tlvText = TlvDecoder.decodeResponse(exchange.response);
    this.store.log('手动 T', Hex.clean(this.inputText), this.manualSession);
    this.store.log('手动 C', `${exchange.response} · ${exchange.rule}\n${exchange.difference}`, this.manualSession);
  }

  private reset(): void {
    this.engine = null;
    this.resultText = '手动会话已重置';
    this.tlvText = '';
  }

  build() {
    Column() {
      Row() {
        Text('APDU 手动调试')
          .fontSize(20)
          .fontColor(Ui.INK)
          .fontWeight(FontWeight.Bold)
          .layoutWeight(1)
        Image($r('app.media.ic_info'))
          .width(24)
          .height(24)
          .fillColor(Ui.MUTED)
          .accessibilityText('调试使用说明')
          .onClick(() => {
            AlertDialog.show({
              title: '调试使用说明',
              message: '选择命令只填充输入框，点击“发送到脚本”才执行。手动与 NFC 会话独立，' +
                '顺序模式仅在匹配成功时推进。固定回复不会重新计算密文或签名。',
              buttons: [{ value: '知道了', action: (): void => {} }]
            });
          })
      }
      .width('100%')

      Text('选择命令')
        .fontSize(13)
        .fontColor(Ui.MUTED)
        .width('100%')
        .margin({ top: 12 })
      Select(this.commandOptions())
        .selected(this.selectedCommand)
        .value(this.selectedLabelText())
        .fontColor(Ui.INK)
        .enabled(this.rules.length > 0)
        .width('100%')
        .onSelect((index: number) => {
          if (index !== this.selectedCommand) {
            this.selectedCommand = index;
            if (index < this.rules.length) {
              this.inputText = this.rules[index].command;
            }
          }
        })

      TextInput({ text: $$this.inputText, placeholder: '输入 C-APDU 十六进制' })
        .fontFamily(Ui.MONO)
        .fontSize(14)
        .fontColor(Ui.INK)
        .placeholderColor(Ui.MUTED)
        .backgroundColor(Ui.FIELD_BG)
        .width('100%')
        .margin({ top: 8 })

      Row({ space: 16 }) {
        Text('发送到脚本')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding({ top: 10, bottom: 10 })
          .onClick(() => {
            this.send();
          })
        Text('重置会话')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding({ top: 10, bottom: 10 })
          .onClick(() => {
            this.reset();
          })
      }
      .width('100%')
      .margin({ top: 8 })

      Text(this.resultText)
        .fontSize(14)
        .fontFamily(Ui.MONO)
        .fontColor(Ui.INK)
        .copyOption(CopyOptions.LocalDevice)
        .width('100%')
        .margin({ top: 12 })
        .textAlign(TextAlign.Start)

      Text('回复 TLV')
        .fontSize(15)
        .fontColor(Ui.INK)
        .fontWeight(FontWeight.Bold)
        .width('100%')
        .margin({ top: 12 })
      Text(this.tlvText)
        .fontSize(12)
        .fontFamily(Ui.MONO)
        .fontColor(Ui.INK)
        .copyOption(CopyOptions.LocalDevice)
        .width('100%')
        .margin({ top: 4 })
        .textAlign(TextAlign.Start)
    }
    .width('100%')
    .padding(16)
    .backgroundColor(Ui.WHITE)
    .borderRadius(Ui.PANEL_RADIUS)
    .alignItems(HorizontalAlign.Start)
  }
}
