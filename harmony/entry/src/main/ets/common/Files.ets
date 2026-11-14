import { fileIo as fs } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';

/** 文件读写工具：同步 IO + tmp 文件 rename 实现原子写。 */
export class Files {
  static exists(path: string): boolean {
    try {
      return fs.accessSync(path);
    } catch (error) {
      return false;
    }
  }

  static readText(path: string): string {
    const stat = fs.statSync(path);
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const buffer = new ArrayBuffer(stat.size);
      const bytes = fs.readSync(file.fd, buffer);
      const decoder = util.TextDecoder.create('utf-8');
      return decoder.decodeToString(new Uint8Array(buffer, 0, bytes));
    } finally {
      fs.closeSync(file);
    }
  }

  static readBytes(path: string): Uint8Array {
    const stat = fs.statSync(path);
    const file = fs.openSync(path, fs.OpenMode.READ_ONLY);
    try {
      const buffer = new ArrayBuffer(stat.size);
      const bytes = fs.readSync(file.fd, buffer);
      return new Uint8Array(buffer, 0, bytes);
    } finally {
      fs.closeSync(file);
    }
  }

  /** 原子写：先写 .tmp，旧文件转存 .bak 后 rename 提交，失败时原文件保持完整。 */
  static writeTextAtomic(path: string, text: string): void {
    const bakPath = `${path}.bak`;
    const tmpPath = `${path}.tmp`;
    if (Files.exists(tmpPath)) {
      fs.unlinkSync(tmpPath);
    }
    const file = fs.openSync(tmpPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, text);
    } finally {
      fs.closeSync(file);
    }
    if (Files.exists(path)) {
      if (Files.exists(bakPath)) {
        fs.unlinkSync(bakPath);
      }
      fs.renameSync(path, bakPath);
    }
    fs.renameSync(tmpPath, path);
    if (Files.exists(bakPath)) {
      fs.unlinkSync(bakPath);
    }
  }

  static writeBytes(path: string, data: Uint8Array): void {
    const tmpPath = `${path}.tmp`;
    if (Files.exists(tmpPath)) {
      fs.unlinkSync(tmpPath);
    }
    const file = fs.openSync(tmpPath, fs.OpenMode.READ_WRITE | fs.OpenMode.CREATE | fs.OpenMode.TRUNC);
    try {
      fs.writeSync(file.fd, data.buffer);
    } finally {
      fs.closeSync(file);
    }
    fs.renameSync(tmpPath, path);
  }
}
