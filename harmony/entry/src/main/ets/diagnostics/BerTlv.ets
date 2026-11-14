import { Hex } from '../core/Hex';

/** 展开后的节点：tag/value 是十六进制文本，depth 为原始嵌套层级，最外层为 0。 */
export class TlvValue {
  readonly tag: string;
  readonly value: string;
  readonly depth: number;

  constructor(tag: string, value: string, depth: number) {
    this.tag = tag;
    this.value = value;
    this.depth = depth;
  }
}

/** 递归读取器：所有偏移和长度均以字节为单位，构造型标签递归展开。 */
class TlvReader {
  private readonly bytes: number[];
  readonly result: TlvValue[] = [];

  constructor(bytes: number[]) {
    this.bytes = bytes;
  }

  read(start: number, limit: number, depth: number): void {
    if (depth > 12) {
      throw new Error('嵌套超过 12 层');
    }
    let offset = start;
    while (offset < limit) {
      const first = this.bytes[offset] & 255;
      const tagStart = offset;
      offset++;
      // 首字节低 5 位均为 1（0x1F）表示标签还有后续字节，例如 9F38。
      if ((first & 31) === 31) {
        let next = 0;
        do {
          if (offset >= limit || offset - tagStart >= 4) {
            throw new Error('Tag 截断或过长');
          }
          next = this.bytes[offset] & 255;
          offset++;
          // 后续标签字节最高位（0x80）为 1 表示仍有下一字节。
        } while ((next & 128) !== 0);
      }
      const tag = Hex.text(this.bytes.slice(tagStart, offset));
      if (offset >= limit) {
        throw new Error(`${tag} 缺少长度`);
      }
      let length = this.bytes[offset] & 255;
      offset++;
      // 短格式：00～7F 直接表示值长度；长格式：低 7 位表示后续长度字节的数量。
      if ((length & 128) !== 0) {
        const count = length & 127;
        // 本实现只接收 1～3 个长度字节；count=0 的不定长编码不受支持。
        if (count < 1 || count > 3 || offset + count > limit) {
          throw new Error(`${tag} 长度编码错误`);
        }
        length = 0;
        // 大端累积：例如 81 80 表示值长 128 字节，82 01 00 表示 256 字节。
        for (let index = 0; index < count; index++) {
          length = (length << 8) | (this.bytes[offset] & 255);
          offset++;
        }
      }
      if (length > limit - offset) {
        throw new Error(`${tag} 声明长度超过剩余数据`);
      }
      this.result.push(new TlvValue(tag, Hex.text(this.bytes.slice(offset, offset + length)), depth));
      // 首字节 0x20 位为 1 表示构造型：值本身仍是一组 TLV，例如 6F、A5。
      if ((first & 32) !== 0) {
        this.read(offset, offset + length, depth + 1);
      }
      offset += length;
    }
  }
}

/** 严格检查整个 BER-TLV 结构后再提供标签，损坏回复不产生部分定义。 */
export class BerTlv {
  /** hex 输入为十六进制文本；损坏输入直接抛异常。 */
  static parseHex(hex: string): TlvValue[] {
    if (!Hex.valid(hex)) {
      throw new Error('TLV 十六进制无效');
    }
    return BerTlv.parseBytes(Hex.bytes(hex));
  }

  /** bytes 输入的 end 是不包含在内的字节结束位置，可排除响应尾部状态字。 */
  static parseBytes(bytes: number[], end: number = bytes.length): TlvValue[] {
    const reader = new TlvReader(bytes);
    reader.read(0, end, 0);
    return reader.result;
  }
}
