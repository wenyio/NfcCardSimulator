import { AppStore } from '../common/AppStore';
import { EditorMode, EditorState } from '../common/EditorState';
import { CardMetadata } from '../core/CardMetadata';
import { CardPacks } from '../core/CardPack';
import { ApduRule, CardScript, MatchMode } from '../core/CardScript';
import { ScriptCodec } from '../core/ScriptCodec';
import { ScriptValidation } from '../core/ScriptValidation';
import { SimulatorStore } from '../data/SimulatorStore';
import { EditorField } from '../components/EditorField';
import { ExpandableSection } from '../components/ExpandableSection';
import { MatchingLabels } from '../components/MatchingLabels';
import { SchemeAddSheet } from '../components/SchemeAddSheet';
import { Ui } from '../components/Ui';

/** 匹配方式下拉顺序；稳定枚举持久化，显示文字仅作展示。 */
const RULE_MODES: MatchMode[] = [MatchMode.EXACT, MatchMode.PREFIX, MatchMode.MASK];

function ruleEquals(a: ApduRule, b: ApduRule): boolean {
  return a.command === b.command && a.response === b.response && a.mode === b.mode &&
    a.mask === b.mask && a.label === b.label;
}

function ruleTitle(rule: ApduRule): string {
  return rule.label.length > 0 ? rule.label : '未命名规则';
}

@Builder
export function CardEditorBuilder() {
  CardEditorPage()
}

/** 卡片与规则编辑页；草稿仅在保存时校验，未保存修改离开前需确认。 */
@Component
struct CardEditorPage {
  @State ready: boolean = false;
  @State errorText: string = '';
  @State editingRule: boolean = false;
  @State rulesSnapshot: ApduRule[] = [];
  @State packIndex: number = 0;
  @State schemeOptions: string[] = [];
  @State schemeIndex: number = 0;
  @State showAddScheme: boolean = false;
  @State orderedIndex: number = 0;
  @State modeIndex: number = 0;
  pathStack: NavPathStack = new NavPathStack();
  appStore: AppStore = AppStore.getInstance();
  store: SimulatorStore = this.appStore.store;
  editor: EditorState | null = null;
  cardDraft: CardScript = new CardScript('', '', '其他', [], '', [], false, '6D00', false, new CardMetadata(), '');
  ruleDraft: ApduRule = new ApduRule('', '9000');
  originalRule: ApduRule = new ApduRule('', '9000');
  editingRuleIndex: number = -1;
  packIds: string[] = [];
  packNames: string[] = [];

  aboutToAppear(): void {
    const editor = this.appStore.editor;
    if (editor === null) {
      this.ready = false;
      return;
    }
    this.editor = editor;
    this.cardDraft = editor.card;
    this.rulesSnapshot = editor.card.rules.slice();
    this.orderedIndex = editor.card.ordered ? 1 : 0;
    this.refreshPackOptions();
    this.refreshSchemeOptions();
    this.ready = true;
  }

  aboutToDisappear(): void {
    if (this.appStore.editorPending) {
      this.appStore.closeEditor();
    }
  }

  private refreshPackOptions(): void {
    const ids: string[] = [];
    const names: string[] = [];
    for (const pack of this.store.packs()) {
      if (!pack.builtIn) {
        ids.push(pack.id);
        names.push(pack.name);
      }
    }
    if (ids.indexOf(this.cardDraft.packId) < 0) {
      this.cardDraft.packId = CardPacks.PERSONAL;
    }
    this.packIds = ids;
    this.packNames = names;
    const found = ids.indexOf(this.cardDraft.packId);
    this.packIndex = found >= 0 ? found : 0;
  }

  private refreshSchemeOptions(): void {
    const options = this.store.schemes.options();
    if (this.cardDraft.scheme.trim().length === 0) {
      this.cardDraft.scheme = '其他';
    }
    const scheme = this.cardDraft.scheme.trim();
    let present = false;
    for (const option of options) {
      if (option.toLowerCase() === scheme.toLowerCase()) {
        present = true;
        break;
      }
    }
    if (!present) {
      options.push(scheme);
    }
    this.schemeOptions = options;
    const found = options.indexOf(scheme);
    this.schemeIndex = found >= 0 ? found : 0;
  }

  private headerTitle(): string {
    if (this.editingRule) {
      return '编辑规则';
    }
    const editor = this.editor;
    if (editor === null) {
      return '';
    }
    if (editor.mode === EditorMode.NEW) {
      return '新建卡片';
    }
    if (editor.mode === EditorMode.COPY) {
      return '复制卡片';
    }
    return '编辑卡片';
  }

  private saveActionText(): string {
    return this.editingRule ? '保存规则' : '保存';
  }

  private aidsText(): string {
    return this.cardDraft.aids.join('\n');
  }

  private packSelectOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    for (const name of this.packNames) {
      options.push({ value: name });
    }
    return options;
  }

  private packSelectedLabel(): string {
    if (this.packIndex < this.packNames.length) {
      return this.packNames[this.packIndex];
    }
    return '';
  }

  private schemeSelectOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    for (const name of this.schemeOptions) {
      options.push({ value: name });
    }
    options.push({ value: '＋ 添加卡组织' });
    return options;
  }

  private schemeSelectedLabel(): string {
    if (this.schemeIndex < this.schemeOptions.length) {
      return this.schemeOptions[this.schemeIndex];
    }
    return '';
  }

  private execSelectOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    options.push({ value: '内容匹配' });
    options.push({ value: '顺序匹配' });
    return options;
  }

  private modeSelectOptions(): SelectOption[] {
    const options: SelectOption[] = [];
    for (const mode of RULE_MODES) {
      options.push({ value: MatchingLabels.mode(mode) });
    }
    return options;
  }

  private modeSelectedLabel(): string {
    if (this.modeIndex < RULE_MODES.length) {
      return MatchingLabels.mode(RULE_MODES[this.modeIndex]);
    }
    return '';
  }

  private showExecHelp(): void {
    AlertDialog.show({
      title: '执行方式',
      message: `${MatchingLabels.execution(this.orderedIndex === 1)}\n` +
        `${MatchingLabels.executionHelp(this.orderedIndex === 1)}`,
      buttons: [{ value: '知道了', action: (): void => {} }]
    });
  }

  private showModeHelp(): void {
    if (this.modeIndex < RULE_MODES.length) {
      AlertDialog.show({
        title: '匹配方式',
        message: MatchingLabels.modeHelp(RULE_MODES[this.modeIndex]),
        buttons: [{ value: '知道了', action: (): void => {} }]
      });
    }
  }

  private editRule(index: number): void {
    if (index >= 0 && index < this.cardDraft.rules.length) {
      this.ruleDraft = ApduRule.clone(this.cardDraft.rules[index]);
      this.originalRule = ApduRule.clone(this.cardDraft.rules[index]);
    } else {
      this.ruleDraft = new ApduRule('', '9000');
      this.originalRule = new ApduRule('', '9000');
    }
    this.editingRuleIndex = index;
    const modeFound = RULE_MODES.indexOf(this.ruleDraft.mode);
    this.modeIndex = modeFound >= 0 ? modeFound : 0;
    this.errorText = '';
    this.editingRule = true;
  }

  private closeRuleForm(): void {
    this.editingRule = false;
    this.editingRuleIndex = -1;
    this.errorText = '';
  }

  private saveRule(): void {
    const candidate = new ApduRule(this.ruleDraft.command, this.ruleDraft.response,
      RULE_MODES[this.modeIndex], this.ruleDraft.mask, this.ruleDraft.label);
    try {
      const normalized = ScriptValidation.rule(candidate, Math.max(this.editingRuleIndex, 0));
      const wrapper = new CardScript('validate', '校验', '其他', ['F001020304'], '', [normalized],
        false, '6D00', false, new CardMetadata(), '');
      ScriptCodec.checkSize(ScriptCodec.encode([wrapper]));
      const rules = this.cardDraft.rules;
      if (this.editingRuleIndex >= 0 && this.editingRuleIndex < rules.length) {
        rules[this.editingRuleIndex] = normalized;
      } else {
        rules.push(normalized);
      }
      this.rulesSnapshot = rules.slice();
      this.closeRuleForm();
    } catch (error) {
      this.errorText = (error as Error).message;
    }
  }

  private moveRule(from: number, to: number): void {
    const rules = this.cardDraft.rules;
    if (from < 0 || from >= rules.length || to < 0 || to >= rules.length) {
      return;
    }
    const rule = rules[from];
    rules[from] = rules[to];
    rules[to] = rule;
    this.rulesSnapshot = rules.slice();
  }

  private removeRule(index: number): void {
    const rules = this.cardDraft.rules;
    if (index < 0 || index >= rules.length) {
      return;
    }
    rules.splice(index, 1);
    this.rulesSnapshot = rules.slice();
  }

  private confirmRemoveRule(index: number): void {
    const rules = this.cardDraft.rules;
    if (index < 0 || index >= rules.length) {
      return;
    }
    AlertDialog.show({
      title: '移除此规则？',
      message: ruleTitle(rules[index]),
      buttons: [
        { value: '取消', action: (): void => {} },
        {
          value: '移除',
          action: (): void => {
            this.removeRule(index);
          }
        }
      ]
    });
  }

  private ruleMenu(index: number): MenuElement[] {
    const count = this.cardDraft.rules.length;
    const menus: MenuElement[] = [];
    menus.push({
      value: '编辑规则',
      action: (): void => {
        this.editRule(index);
      }
    });
    menus.push({
      value: '上移',
      enabled: index > 0,
      action: (): void => {
        this.moveRule(index, index - 1);
      }
    });
    menus.push({
      value: '下移',
      enabled: index < count - 1,
      action: (): void => {
        this.moveRule(index, index + 1);
      }
    });
    menus.push({
      value: '移除',
      action: (): void => {
        this.confirmRemoveRule(index);
      }
    });
    return menus;
  }

  private saveCard(): void {
    const editor = this.editor;
    if (editor === null) {
      return;
    }
    try {
      const saved = editor.validated();
      this.store.saveEditedCard(saved, editor.originalKey);
      this.store.switchPack(saved.packId);
      if (this.appStore.editorFromDetail) {
        this.appStore.session.open(saved.key);
        this.appStore.session.setRequested(this.appStore.editorRequested);
      }
      this.appStore.closeEditor();
      this.pathStack.pop();
    } catch (error) {
      this.errorText = (error as Error).message;
    }
  }

  private discard(): void {
    this.appStore.closeEditor();
    this.pathStack.pop();
  }

  private confirmDiscardCard(): void {
    AlertDialog.show({
      title: '放弃未保存的修改？',
      message: '返回后本次编辑内容不会保存。',
      buttons: [
        {
          value: '继续编辑',
          action: (): void => {}
        },
        {
          value: '放弃修改',
          action: (): void => {
            this.discard();
          }
        }
      ]
    });
  }

  private confirmDiscardRule(): void {
    AlertDialog.show({
      title: '放弃未保存的修改？',
      message: '返回后本次编辑内容不会保存。',
      buttons: [
        {
          value: '继续编辑',
          action: (): void => {}
        },
        {
          value: '放弃修改',
          action: (): void => {
            this.closeRuleForm();
          }
        }
      ]
    });
  }

  private back(): void {
    if (this.editingRule) {
      if (ruleEquals(this.ruleDraft, this.originalRule)) {
        this.closeRuleForm();
      } else {
        this.confirmDiscardRule();
      }
      return;
    }
    const editor = this.editor;
    if (editor !== null && editor.dirty) {
      this.confirmDiscardCard();
    } else {
      this.discard();
    }
  }

  private saveAction(): void {
    if (this.editingRule) {
      this.saveRule();
    } else {
      this.saveCard();
    }
  }

  @Builder
  addSchemeSheetBuilder() {
    SchemeAddSheet({
      onAdded: (scheme: string): void => {
        this.showAddScheme = false;
        this.cardDraft.scheme = scheme;
        this.refreshSchemeOptions();
      },
      onClose: (): void => {
        this.showAddScheme = false;
      }
    })
  }

  @Builder
  cardFormBuilder() {
    Column() {
      if (this.errorText.length > 0) {
        Text(this.errorText)
          .fontSize(13)
          .fontColor(Ui.DANGER)
          .width('100%')
          .padding({ left: 16, right: 16, top: 12 })
      }

      Column() {
        Text('基本信息')
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(Ui.INK)
          .width('100%')
        EditorField({
          fieldLabel: '卡片名称',
          initialText: this.cardDraft.name,
          onInput: (text: string): void => {
            this.cardDraft.name = text;
          }
        })
          .margin({ top: 12 })

        Text('所属卡包')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        Select(this.packSelectOptions())
          .selected(this.packIndex)
          .value(this.packSelectedLabel())
          .fontColor(Ui.INK)
          .width('100%')
          .margin({ top: 4 })
          .onSelect((index: number) => {
            if (index < this.packIds.length) {
              this.packIndex = index;
              this.cardDraft.packId = this.packIds[index];
            }
          })

        Text('卡组织')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        Select(this.schemeSelectOptions())
          .selected(this.schemeIndex)
          .value(this.schemeSelectedLabel())
          .fontColor(Ui.INK)
          .width('100%')
          .margin({ top: 4 })
          .onSelect((index: number) => {
            if (index >= this.schemeOptions.length) {
              this.showAddScheme = true;
              return;
            }
            this.schemeIndex = index;
            this.cardDraft.scheme = this.schemeOptions[index];
          })
          .bindSheet($$this.showAddScheme, this.addSchemeSheetBuilder(), {
            height: SheetSize.FIT_CONTENT,
            onDisappear: (): void => {
              this.showAddScheme = false;
            }
          })

        Text('AID（每行一个）')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        EditorField({
          fieldLabel: '',
          placeholder: 'AID',
          initialText: this.aidsText(),
          multiline: true,
          mono: true,
          onInput: (text: string): void => {
            const aids: string[] = [];
            for (const line of text.split('\n')) {
              const value = line.trim();
              if (value.length > 0) {
                aids.push(value);
              }
            }
            this.cardDraft.aids = aids;
          }
        })
          .margin({ top: 4 })

        Text('说明')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        EditorField({
          fieldLabel: '',
          placeholder: '卡片说明',
          initialText: this.cardDraft.description,
          multiline: true,
          onInput: (text: string): void => {
            this.cardDraft.description = text;
          }
        })
          .margin({ top: 4 })
      }
      .width('100%')
      .padding(16)
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ left: 16, right: 16, top: 12 })
      .alignItems(HorizontalAlign.Start)

      Column() {
        Row() {
          Text('执行设置')
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
              this.showExecHelp();
            })
        }
        .width('100%')

        Text('执行方式')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        Select(this.execSelectOptions())
          .selected(this.orderedIndex)
          .value(this.orderedIndex === 1 ? '顺序匹配' : '内容匹配')
          .fontColor(Ui.INK)
          .width('100%')
          .margin({ top: 4 })
          .onSelect((index: number) => {
            this.orderedIndex = index;
            this.cardDraft.ordered = index === 1;
          })

        Text('未匹配时回复（十六进制）')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        EditorField({
          fieldLabel: '',
          placeholder: '未匹配时回复',
          initialText: this.cardDraft.fallback,
          mono: true,
          onInput: (text: string): void => {
            this.cardDraft.fallback = text;
          }
        })
          .margin({ top: 4 })
      }
      .width('100%')
      .padding(16)
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ left: 16, right: 16, top: 12 })
      .alignItems(HorizontalAlign.Start)

      Column() {
        Row() {
          Text(`APDU 规则 · ${this.rulesSnapshot.length} 条`)
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(Ui.INK)
            .layoutWeight(1)
        }
        .width('100%')

        ForEach(this.rulesSnapshot, (rule: ApduRule, index: number) => {
          Row() {
            Text(`${index + 1}. ${ruleTitle(rule)}`)
              .fontSize(15)
              .fontColor(Ui.INK)
              .layoutWeight(1)
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
            Image($r('app.media.ic_more'))
              .width(32)
              .height(32)
              .objectFit(ImageFit.Contain)
              .fillColor(Ui.INK)
              .padding(4)
              .accessibilityText('更多操作')
              .hitTestBehavior(HitTestMode.Block)
              .bindMenu(this.ruleMenu(index))
          }
          .width('100%')
          .height(44)
          .alignItems(VerticalAlign.Center)
          .onClick(() => {
            this.editRule(index);
          })
        }, (rule: ApduRule, index: number) =>
          `${index}-${rule.command}-${rule.response}-${rule.mode}-${rule.label}`)

        Text('添加规则')
          .fontSize(15)
          .fontColor(Ui.BLUE)
          .padding({ top: 8, bottom: 4 })
          .onClick(() => {
            this.editRule(-1);
          })
      }
      .width('100%')
      .padding(16)
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ left: 16, right: 16, top: 12 })
      .alignItems(HorizontalAlign.Start)

      Column() {
        ExpandableSection({ sectionTitle: '测试场景（可选）' }) {
          EditorField({
            fieldLabel: '测试分类',
            initialText: this.cardDraft.metadata.scenario.category,
            onInput: (text: string): void => {
              this.cardDraft.metadata.scenario.category = text;
            }
          })
            .margin({ top: 8 })
          EditorField({
            fieldLabel: '测试目的',
            initialText: this.cardDraft.metadata.scenario.purpose,
            onInput: (text: string): void => {
              this.cardDraft.metadata.scenario.purpose = text;
            }
          })
            .margin({ top: 12 })
          EditorField({
            fieldLabel: '预期结果',
            initialText: this.cardDraft.metadata.scenario.expectedResult,
            onInput: (text: string): void => {
              this.cardDraft.metadata.scenario.expectedResult = text;
            }
          })
            .margin({ top: 12 })
          EditorField({
            fieldLabel: '测试条件',
            initialText: this.cardDraft.metadata.scenario.conditions,
            multiline: true,
            onInput: (text: string): void => {
              this.cardDraft.metadata.scenario.conditions = text;
            }
          })
            .margin({ top: 12 })
          EditorField({
            fieldLabel: '交易金额',
            initialText: this.cardDraft.metadata.scenario.amount,
            onInput: (text: string): void => {
              this.cardDraft.metadata.scenario.amount = text;
            }
          })
            .margin({ top: 12 })
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
    }
    .width('100%')
  }

  @Builder
  ruleFormBuilder() {
    Column() {
      if (this.errorText.length > 0) {
        Text(this.errorText)
          .fontSize(13)
          .fontColor(Ui.DANGER)
          .width('100%')
          .padding({ left: 16, right: 16, top: 12 })
      }

      Column() {
        Text('规则说明')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
        EditorField({
          fieldLabel: '',
          placeholder: '规则说明',
          initialText: this.ruleDraft.label,
          onInput: (text: string): void => {
            this.ruleDraft.label = text;
          }
        })
          .margin({ top: 4 })

        Text('命令 C-APDU（十六进制）')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        EditorField({
          fieldLabel: '',
          placeholder: '命令 C-APDU',
          initialText: this.ruleDraft.command,
          multiline: true,
          mono: true,
          onInput: (text: string): void => {
            this.ruleDraft.command = text;
          }
        })
          .margin({ top: 4 })

        Text('回复 R-APDU（十六进制）')
          .fontSize(13)
          .fontColor(Ui.MUTED)
          .width('100%')
          .margin({ top: 12 })
        EditorField({
          fieldLabel: '',
          placeholder: '回复 R-APDU',
          initialText: this.ruleDraft.response,
          multiline: true,
          mono: true,
          onInput: (text: string): void => {
            this.ruleDraft.response = text;
          }
        })
          .margin({ top: 4 })

        Row() {
          Text('匹配方式')
            .fontSize(13)
            .fontColor(Ui.MUTED)
            .layoutWeight(1)
          Image($r('app.media.ic_info'))
            .width(24)
            .height(24)
            .objectFit(ImageFit.Contain)
            .fillColor(Ui.MUTED)
            .accessibilityText('查看说明')
            .onClick(() => {
              this.showModeHelp();
            })
        }
        .width('100%')
        .margin({ top: 12 })
        Select(this.modeSelectOptions())
          .selected(this.modeIndex)
          .value(this.modeSelectedLabel())
          .fontColor(Ui.INK)
          .width('100%')
          .margin({ top: 4 })
          .onSelect((index: number) => {
            if (index < RULE_MODES.length) {
              this.modeIndex = index;
            }
          })

        if (this.modeIndex === 2) {
          Text('掩码 MASK（与命令等长，FF 比较、00 忽略）')
            .fontSize(13)
            .fontColor(Ui.MUTED)
            .width('100%')
            .margin({ top: 12 })
          EditorField({
            fieldLabel: '',
            placeholder: '掩码 MASK',
            initialText: this.ruleDraft.mask,
            multiline: true,
            mono: true,
            onInput: (text: string): void => {
              this.ruleDraft.mask = text;
            }
          })
            .margin({ top: 4 })
        }

        if (this.modeIndex !== 0) {
          Text(MatchingLabels.fixedReplyText)
            .fontSize(12)
            .fontColor(Ui.MUTED)
            .width('100%')
            .margin({ top: 12 })
        }
      }
      .width('100%')
      .padding(16)
      .backgroundColor(Ui.WHITE)
      .borderRadius(Ui.PANEL_RADIUS)
      .margin({ left: 16, right: 16, top: 12 })
      .alignItems(HorizontalAlign.Start)

      Text('保存规则')
        .fontSize(15)
        .fontColor(Ui.BLUE)
        .padding({ top: 16, bottom: 16 })
        .onClick(() => {
          this.saveRule();
        })
    }
    .width('100%')
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
              this.back();
            })
          Text(this.headerTitle())
            .fontSize(18)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .layoutWeight(1)
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          Text(this.saveActionText())
            .fontSize(15)
            .fontColor(Color.White)
            .padding({ left: 12, right: 12, top: 8, bottom: 8 })
            .onClick(() => {
              this.saveAction();
            })
        }
        .width('100%')
        .height(56)
        .padding({ left: 4, right: 12 })
        .backgroundColor(Ui.INK)
        .alignItems(VerticalAlign.Center)

        if (this.ready) {
          Scroll() {
            Column() {
              if (this.editingRule) {
                this.ruleFormBuilder()
              } else {
                this.cardFormBuilder()
              }
            }
            .width('100%')
            .padding({ bottom: 32 })
          }
          .align(Alignment.TopStart)
          .layoutWeight(1)
          .width('100%')
        } else {
          Column()
            .layoutWeight(1)
            .width('100%')
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Ui.PAGE_BG)
    }
    .hideTitleBar(true)
    .onReady((ctx: NavDestinationContext) => {
      this.pathStack = ctx.pathStack;
    })
    .onBackPressed(() => {
      this.back();
      return true;
    })
  }
}
