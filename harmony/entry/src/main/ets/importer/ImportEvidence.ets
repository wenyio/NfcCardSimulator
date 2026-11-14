import { cryptoFramework } from '@kit.CryptoArchitectureKit';
import { CardMetadata, CardSource } from '../core/CardMetadata';
import { CardScript } from '../core/CardScript';
import { Hex } from '../core/Hex';

/** 对原始文件字节计算指纹；已携带的历史来源不会被本次导入文件覆盖。 */
export class ImportEvidence {
  readonly fileName: string;
  readonly sha256: string;

  constructor(fileName: string, sha256: string) {
    this.fileName = fileName;
    this.sha256 = sha256;
  }

  /** 对原始文件字节计算完整 SHA-256 指纹并限制文件名长度；字节包含 BOM 或换行差异时哈希也会不同。 */
  static from(bytes: Uint8Array, fileName: string): ImportEvidence {
    const md = cryptoFramework.createMd('SHA256');
    const blob: cryptoFramework.DataBlob = { data: bytes };
    md.updateSync(blob);
    const digest = md.digestSync();
    return new ImportEvidence(fileName.substring(0, 512), Hex.fromUint8(digest.data).toLowerCase());
  }

  /**
   * 为尚无文件名和哈希的卡片补上本次来源证据，已有证据保持配对，不混入新文件名。
   * 仅补齐空格式说明；来源信息不参与规则匹配，也不证明交易成功。
   */
  attach(card: CardScript, defaultFormat: string): CardScript {
    const source = card.metadata.source;
    // 文件名和哈希为同一份证据，不将历史哈希与新文件名混合。
    const origin = source.fileName.trim().length === 0 && source.sha256.trim().length === 0 ?
      new CardSource(source.format, this.fileName, this.sha256, source.originalCardId, source.configId)
      : CardSource.clone(source);
    const format = origin.format.trim().length === 0 ? defaultFormat : origin.format;
    const metadata = new CardMetadata(
      new CardSource(format, origin.fileName, origin.sha256, origin.originalCardId, origin.configId),
      card.metadata.scenario, card.metadata.warnings);
    const result = CardScript.clone(card);
    result.metadata = metadata;
    return result;
  }
}
