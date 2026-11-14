import { Ui } from './Ui';

/** 标签加输入框；输入回调整体提交给页面，组件不持有草稿。fieldLabel 为空时不渲染标签，由外层标题承担。 */
@Component
export struct EditorField {
  fieldLabel: string = '';
  placeholder: string = '';
  initialText: string = '';
  multiline: boolean = false;
  mono: boolean = false;
  onInput: (text: string) => void = () => {};
  @State text: string = '';

  aboutToAppear(): void {
    this.text = this.initialText;
  }

  private fontFamilyName(): string {
    return this.mono ? Ui.MONO : 'HarmonyOS Sans';
  }

  private hint(): string {
    return this.placeholder.length > 0 ? this.placeholder : this.fieldLabel;
  }

  build() {
    Column() {
      if (this.fieldLabel.length > 0) {
        Text(this.fieldLabel)
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
      }
      if (this.multiline) {
        TextArea({ text: $$this.text, placeholder: this.hint() })
          .fontFamily(this.fontFamilyName())
          .fontSize(14)
          .fontColor(Ui.INK)
          .placeholderColor(Ui.MUTED)
          .backgroundColor(Ui.FIELD_BG)
          .width('100%')
          .margin({ top: 4 })
          .onChange((value: string) => {
            this.onInput(value);
          })
      } else {
        TextInput({ text: $$this.text, placeholder: this.hint() })
          .fontFamily(this.fontFamilyName())
          .fontSize(14)
          .fontColor(Ui.INK)
          .placeholderColor(Ui.MUTED)
          .backgroundColor(Ui.FIELD_BG)
          .width('100%')
          .margin({ top: 4 })
          .onChange((value: string) => {
            this.onInput(value);
          })
      }
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }
}
