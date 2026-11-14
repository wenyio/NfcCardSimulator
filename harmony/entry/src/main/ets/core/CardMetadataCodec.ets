import { CardMetadata, CardSource, TestScenario } from './CardMetadata';
import { Json } from './Json';

interface RawSourceJson {
  format: string;
  fileName: string;
  sha256: string;
  originalCardId: string;
  configId: string;
}

interface RawScenarioJson {
  category: string;
  purpose: string;
  expectedResult: string;
  conditions: string;
  amount: string;
  legacyPdol: string;
  legacyCdol: string;
}

interface RawMetadataJson {
  source: RawSourceJson;
  scenario: RawScenarioJson;
  warnings: string[];
}

export class CardMetadataCodec {
  private static text(record: Record<string, Object>, key: string): string {
    const value = record[key];
    if (value === undefined) {
      return '';
    }
    if (typeof value !== 'string') {
      throw new Error(`metadata.${key} 须为字符串`);
    }
    return value;
  }

  private static bounded(value: string, key: string, limit: number = 4000): string {
    if (value.length > limit) {
      throw new Error(`metadata.${key} 过长`);
    }
    return value;
  }

  static validate(value: CardMetadata): CardMetadata {
    const source = value.source;
    const scenario = value.scenario;
    const sha = CardMetadataCodec.bounded(source.sha256, 'sha256', 64);
    if (sha.length > 0 && !/^[a-fA-F0-9]{64}$/.test(sha)) {
      throw new Error('来源 SHA-256 须为 64 位十六进制');
    }
    if (value.warnings.length > 30) {
      throw new Error('转换提示最多 30 条');
    }
    for (const warning of value.warnings) {
      if (warning.length > 2000) {
        throw new Error('转换提示过长');
      }
    }
    return new CardMetadata(
      new CardSource(
        CardMetadataCodec.bounded(source.format, 'format', 120),
        CardMetadataCodec.bounded(source.fileName, 'fileName', 512),
        sha.toLowerCase(),
        CardMetadataCodec.bounded(source.originalCardId, 'originalCardId', 120),
        CardMetadataCodec.bounded(source.configId, 'configId', 240)),
      new TestScenario(
        CardMetadataCodec.bounded(scenario.category, 'category', 120),
        CardMetadataCodec.bounded(scenario.purpose, 'purpose'),
        CardMetadataCodec.bounded(scenario.expectedResult, 'expectedResult'),
        CardMetadataCodec.bounded(scenario.conditions, 'conditions'),
        CardMetadataCodec.bounded(scenario.amount, 'amount', 120),
        CardMetadataCodec.bounded(scenario.legacyPdol, 'legacyPdol'),
        CardMetadataCodec.bounded(scenario.legacyCdol, 'legacyCdol')),
      value.warnings.slice());
  }

  private static child(record: Record<string, Object>, key: string): Record<string, Object> {
    const value = record[key];
    if (value === undefined) {
      return {};
    }
    const child = Json.asRecord(value);
    if (child === null) {
      throw new Error(`metadata.${key} 须为对象`);
    }
    return child;
  }

  static parse(record: Record<string, Object>): CardMetadata {
    const metadataValue = record['metadata'];
    if (metadataValue === undefined) {
      return new CardMetadata();
    }
    const metadata = Json.asRecord(metadataValue);
    if (metadata === null) {
      throw new Error('metadata 须为对象');
    }
    const source = CardMetadataCodec.child(metadata, 'source');
    const scenario = CardMetadataCodec.child(metadata, 'scenario');
    const messages: string[] = [];
    const warningsValue = metadata['warnings'];
    if (warningsValue !== undefined) {
      const warnings = Json.asArray(warningsValue);
      if (warnings === null) {
        throw new Error('warnings 须为数组');
      }
      for (const item of warnings) {
        if (typeof item !== 'string') {
          throw new Error('转换提示须为字符串');
        }
        messages.push(item);
      }
    }
    return CardMetadataCodec.validate(new CardMetadata(
      new CardSource(
        CardMetadataCodec.text(source, 'format'),
        CardMetadataCodec.text(source, 'fileName'),
        CardMetadataCodec.text(source, 'sha256'),
        CardMetadataCodec.text(source, 'originalCardId'),
        CardMetadataCodec.text(source, 'configId')),
      new TestScenario(
        CardMetadataCodec.text(scenario, 'category'),
        CardMetadataCodec.text(scenario, 'purpose'),
        CardMetadataCodec.text(scenario, 'expectedResult'),
        CardMetadataCodec.text(scenario, 'conditions'),
        CardMetadataCodec.text(scenario, 'amount'),
        CardMetadataCodec.text(scenario, 'legacyPdol'),
        CardMetadataCodec.text(scenario, 'legacyCdol')),
      messages));
  }

  static encode(value: CardMetadata): RawMetadataJson {
    const source = value.source;
    const scenario = value.scenario;
    return {
      source: {
        format: source.format,
        fileName: source.fileName,
        sha256: source.sha256,
        originalCardId: source.originalCardId,
        configId: source.configId
      },
      scenario: {
        category: scenario.category,
        purpose: scenario.purpose,
        expectedResult: scenario.expectedResult,
        conditions: scenario.conditions,
        amount: scenario.amount,
        legacyPdol: scenario.legacyPdol,
        legacyCdol: scenario.legacyCdol
      },
      warnings: value.warnings.slice()
    };
  }
}
