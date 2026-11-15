import { common } from '@kit.AbilityKit';
import { util } from '@kit.ArkTS';
import { picker, fileIo as fs } from '@kit.CoreFileKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { CardPack } from '../core/CardPack';
import { CardPackCodec } from '../core/CardPackCodec';
import { CardScript } from '../core/CardScript';
import { ScriptCodec } from '../core/ScriptCodec';
import { SimulatorStore } from '../data/SimulatorStore';
import { CapturedTransaction, TransactionLogParser } from '../importer/TransactionLogParser';
import { ImportEvidence } from '../importer/ImportEvidence';

/**
 * 文件访问与解析；页面只负责触发操作。
 * 导入按大小上限读取文档，计算原始证据并分流 JSON 或交易文本解析；
 * 导出经系统保存选择器写入用户选定的目标位置。
 */
export class DocumentController {
  private readonly context: common.Context;
  private readonly store: SimulatorStore;
  private readonly onImported: () => void;
  private readonly message: (text: string) => void;
  private readonly showPackImport: (pack: CardPack) => void;
  private readonly showTransactionImport: (transactions: CapturedTransaction[], evidence: ImportEvidence) => void;

  constructor(context: common.Context, store: SimulatorStore,
    showPackImport: (pack: CardPack) => void,
    showTransactionImport: (transactions: CapturedTransaction[], evidence: ImportEvidence) => void,
    onImported: () => void, message: (text: string) => void) {
    this.context = context;
    this.store = store;
    this.showPackImport = showPackImport;
    this.showTransactionImport = showTransactionImport;
    this.onImported = onImported;
    this.message = message;
  }

  /** 启动系统的文件选择器；选中文档后才读取。 */
  import(): void {
    const documentPicker = new picker.DocumentViewPicker(this.context);
    documentPicker.select().then((uris: Array<string>) => {
      if (uris.length === 0) {
        return;
      }
      this.read(uris[0]);
    }).catch((error: BusinessError): void => {
      this.message(`导入失败：${error.message}`);
    });
  }

  /** 暂存单卡 JSON 并打开系统保存界面；用户选定目标后写出。 */
  exportCard(card: CardScript): void {
    this.save(ScriptCodec.encode([card]), `${card.id}.json`);
  }

  /** 暂存完整卡包 JSON 并打开系统保存界面，不直接写任意外部路径。 */
  exportPack(pack: CardPack): void {
    this.save(CardPackCodec.encode(pack), `${pack.id}.json`);
  }

  /** 按可选卡片过滤导出日志，暂存文本后请系统保存；null 表示全部日志。 */
  exportLogs(cardId: string | null): void {
    const scope = cardId === null ? 'all' : (this.store.findCard(cardId)?.name ?? cardId);
    let safeName = '';
    for (let index = 0; index < scope.length; index++) {
      const letter = scope.charAt(index);
      const code = scope.charCodeAt(index);
      safeName += code < 32 || code === 127 || '\\/:*?"<>|'.includes(letter) || letter.trim() === '' ? '_' : letter;
    }
    const name = safeName.replace(new RegExp('^[ ._]+|[ ._]+$', 'g'), '').substring(0, 60) || 'card';
    const date = new Date();
    const timestamp = `${date.getFullYear()}${DocumentController.pad(date.getMonth() + 1)}${DocumentController.pad(date.getDate())}`
      + '-' + DocumentController.pad(date.getHours()) + DocumentController.pad(date.getMinutes())
      + DocumentController.pad(date.getSeconds()) + '-' + DocumentController.pad(date.getMilliseconds(), 3);
    this.save(this.store.logText(cardId), `nfc-apdu-log_${name}_${timestamp}.txt`);
  }

  private static pad(part: number, width: number = 2): string {
    return part.toString().padStart(width, '0');
  }

  /** 按大小上限读取文档，计算原始证据并分流 JSON 或交易文本解析；成功后展示预览。 */
  private read(uri: string): void {
    try {
      const bytes = DocumentController.readUri(uri, ScriptCodec.MAX_BYTES);
      const decoder = util.TextDecoder.create('utf-8');
      const rawText = decoder.decodeToString(bytes);
      const text = rawText.charCodeAt(0) === 0xFEFF ? rawText.substring(1) : rawText;
      const evidence = ImportEvidence.from(bytes, '');
      const trimmed = text.trimStart();
      if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
        const parsed = CardPackCodec.parse(text, '导入卡包', false, false);
        const cards: CardScript[] = [];
        for (const card of parsed.cards) {
          cards.push(evidence.attach(card, '卡片 JSON'));
        }
        const pack = CardPack.clone(parsed);
        pack.cards = cards;
        this.showPackImport(pack);
      } else {
        this.showTransactionImport(TransactionLogParser.parse(text), evidence);
      }
    } catch (error) {
      this.message(`导入失败：${(error as Error).message}`);
    }
  }

  private save(text: string, fileName: string): void {
    const savePicker = new picker.DocumentViewPicker(this.context);
    const options = new picker.DocumentSaveOptions();
    options.newFileNames = [fileName];
    savePicker.save(options).then((uris: Array<string>) => {
      if (uris.length === 0) {
        return;
      }
      try {
        DocumentController.writeUri(uris[0], text);
        this.message('导出完成');
      } catch (error) {
        this.message(`导出失败：${(error as Error).message}`);
      }
    }).catch((error: BusinessError): void => {
      this.message(`导出失败：${error.message}`);
    });
  }

  private static readUri(uri: string, maxBytes: number): Uint8Array {
    const file = fs.openSync(uri, fs.OpenMode.READ_ONLY);
    try {
      const chunks: Uint8Array[] = [];
      let total = 0;
      const buffer = new ArrayBuffer(8192);
      while (true) {
        const bytes = fs.readSync(file.fd, buffer);
        if (bytes <= 0) {
          break;
        }
        if (total + bytes > maxBytes) {
          throw new Error('文件超过 2 MB');
        }
        chunks.push(new Uint8Array(buffer.slice(0, bytes)));
        total += bytes;
      }
      const result = new Uint8Array(total);
      let offset = 0;
      for (const chunk of chunks) {
        result.set(chunk, offset);
        offset += chunk.length;
      }
      if (total === 0) {
        throw new Error('无法读取文件');
      }
      return result;
    } finally {
      fs.closeSync(file);
    }
  }

  private static writeUri(uri: string, text: string): void {
    const file = fs.openSync(uri, fs.OpenMode.READ_WRITE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, text);
    } finally {
      fs.closeSync(file);
    }
  }
}
