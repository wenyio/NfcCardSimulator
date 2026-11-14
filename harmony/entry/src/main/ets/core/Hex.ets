export class Hex {
  static clean(value: string): string {
    return value.replace(/\s+/g, '').toUpperCase();
  }

  static valid(value: string): boolean {
    return value.length % 2 === 0 && /^[0-9A-F]*$/.test(value);
  }

  static bytes(value: string): number[] {
    const text = Hex.clean(value);
    if (!Hex.valid(text)) {
      throw new Error('十六进制格式不正确');
    }
    const result: number[] = [];
    for (let index = 0; index < text.length; index += 2) {
      result.push(parseInt(text.substring(index, index + 2), 16));
    }
    return result;
  }

  static text(bytes: number[]): string {
    let result = '';
    for (const byte of bytes) {
      const value = byte & 0xFF;
      const digit = value.toString(16).toUpperCase();
      result += digit.length < 2 ? `0${digit}` : digit;
    }
    return result;
  }

  static fromUint8(data: Uint8Array): string {
    let result = '';
    for (let index = 0; index < data.length; index++) {
      const value = data[index] & 0xFF;
      const digit = value.toString(16).toUpperCase();
      result += digit.length < 2 ? `0${digit}` : digit;
    }
    return result;
  }
}
