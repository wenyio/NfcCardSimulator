import { util } from '@kit.ArkTS';
import { CardMetadata } from '../core/CardMetadata';
import { CardPacks } from '../core/CardPack';
import { CardScript } from '../core/CardScript';
import { ScriptCodec } from '../core/ScriptCodec';
import { ScriptValidation } from '../core/ScriptValidation';

/** 编辑意图的稳定状态值；保存/恢复不依赖中文显示文本。 */
export enum EditorMode {
  NEW = 'NEW',
  COPY = 'COPY',
  EDIT = 'EDIT'
}

/** 草稿只在保存时校验，半成品也能在页面间传递；不能用导入校验器读取未完成的输入。 */
export class EditorState {
  card: CardScript;
  readonly initial: string;
  readonly mode: EditorMode;
  readonly originalKey: string | null;
  readonly initialPackId: string;

  constructor(card: CardScript, initial: string, mode: EditorMode,
    originalKey: string | null, initialPackId: string) {
    this.card = card;
    this.initial = initial;
    this.mode = mode;
    this.originalKey = originalKey;
    this.initialPackId = initialPackId;
  }

  /** 单卡导出不含包归属，所以除内容快照外还必须单独比较 packId，防止遗漏跨包移动。 */
  get dirty(): boolean {
    return ScriptCodec.encode([this.card]) !== this.initial || this.card.packId !== this.initialPackId;
  }

  /** 保存前检查编码后的大小并规范化完整卡片，返回可写的用户卡副本；不执行持久化。 */
  validated(): CardScript {
    ScriptCodec.checkSize(ScriptCodec.encode([this.card]));
    const normalized = ScriptValidation.card(this.card);
    normalized.builtIn = false;
    return normalized;
  }

  /**
   * 按新建、复制或编辑创建工作副本：新建/复制生成新 ID，编辑保留原身份和归属。
   * 记录初始快照用于判断未保存修改，原卡内容不受草稿修改影响。
   */
  static create(source: CardScript | null, copy: boolean,
    targetPackId: string = CardPacks.PERSONAL): EditorState {
    let card: CardScript;
    if (source === null) {
      card = new CardScript(`card-${util.generateRandomUUID(false)}`, '', '其他', [], '', [], false,
        '6D00', false, new CardMetadata(), targetPackId);
    } else if (copy) {
      card = CardScript.clone(source);
      card.id = `card-${util.generateRandomUUID(false)}`;
      card.packId = targetPackId;
      card.name = `${source.name} 副本`;
      card.builtIn = false;
    } else {
      card = CardScript.clone(source);
      card.packId = source.packId.length > 0 ? source.packId : CardPacks.PERSONAL;
      card.builtIn = false;
    }
    const initial = ScriptCodec.encode([card]);
    const mode = source === null ? EditorMode.NEW : (copy ? EditorMode.COPY : EditorMode.EDIT);
    const originalKey = source !== null && !copy ? card.key : null;
    return new EditorState(card, initial, mode, originalKey, card.packId);
  }
}
