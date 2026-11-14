import { Hex } from '../core/Hex';
import { BerTlv } from './BerTlv';

/** BER-TLV 查看：先检查边界，再读长度/值，限制嵌套以处理损坏日志。 */
export class TlvDecoder {
  /**
   * 先分离响应最后两字节 SW1/SW2，再把前面的数据按 BER-TLV 展示；解析失败返回提示文字。
   * SW（Status Word，状态字）描述命令执行结果；这里仅解码展示，不修改脚本回复。
   */
  static decodeResponse(response: string): string {
    try {
      const bytes = Hex.bytes(response);
      if (bytes.length < 2) {
        throw new Error('缺少 SW1/SW2');
      }
      const end = bytes.length - 2;
      const lines: string[] = [`SW=${Hex.text(bytes.slice(end, bytes.length))}`];
      const values = BerTlv.parseBytes(bytes, end);
      for (const item of values) {
        lines.push(`${'  '.repeat(item.depth)}${item.tag} [${item.value.length / 2}] ${item.value}`);
      }
      return lines.join('\n');
    } catch (error) {
      const err = error as Error;
      return `非完整 BER-TLV：${err.message}`;
    }
  }
}
