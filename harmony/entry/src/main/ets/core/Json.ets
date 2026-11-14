export class Json {
  static parse(text: string): Object {
    return JSON.parse(text) as Object;
  }

  static asRecord(value: Object | undefined): Record<string, Object> | null {
    if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
      return value as Record<string, Object>;
    }
    return null;
  }

  static asArray(value: Object | undefined): Object[] | null {
    if (typeof value === 'object' && value !== null && Array.isArray(value)) {
      return value as Object[];
    }
    return null;
  }

  static str(value: Object | undefined, fallback: string): string {
    return typeof value === 'string' ? value : fallback;
  }

  static num(value: Object | undefined, fallback: number): number {
    return typeof value === 'number' ? value : fallback;
  }

  static bool(value: Object | undefined, fallback: boolean): boolean {
    return typeof value === 'boolean' ? value : fallback;
  }
}
