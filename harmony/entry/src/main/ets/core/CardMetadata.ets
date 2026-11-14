export class CardSource {
  format: string;
  fileName: string;
  sha256: string;
  originalCardId: string;
  configId: string;

  constructor(format: string = '', fileName: string = '', sha256: string = '',
    originalCardId: string = '', configId: string = '') {
    this.format = format;
    this.fileName = fileName;
    this.sha256 = sha256;
    this.originalCardId = originalCardId;
    this.configId = configId;
  }

  static clone(source: CardSource): CardSource {
    return new CardSource(source.format, source.fileName, source.sha256,
      source.originalCardId, source.configId);
  }

  get isEmpty(): boolean {
    return this.format.length === 0 && this.fileName.length === 0 && this.sha256.length === 0 &&
      this.originalCardId.length === 0 && this.configId.length === 0;
  }
}

export class TestScenario {
  category: string;
  purpose: string;
  expectedResult: string;
  conditions: string;
  amount: string;
  legacyPdol: string;
  legacyCdol: string;

  constructor(category: string = '', purpose: string = '', expectedResult: string = '',
    conditions: string = '', amount: string = '', legacyPdol: string = '', legacyCdol: string = '') {
    this.category = category;
    this.purpose = purpose;
    this.expectedResult = expectedResult;
    this.conditions = conditions;
    this.amount = amount;
    this.legacyPdol = legacyPdol;
    this.legacyCdol = legacyCdol;
  }

  static clone(scenario: TestScenario): TestScenario {
    return new TestScenario(scenario.category, scenario.purpose, scenario.expectedResult,
      scenario.conditions, scenario.amount, scenario.legacyPdol, scenario.legacyCdol);
  }
}

export class CardMetadata {
  source: CardSource;
  scenario: TestScenario;
  warnings: string[];

  constructor(source: CardSource = new CardSource(), scenario: TestScenario = new TestScenario(),
    warnings: string[] = []) {
    this.source = source;
    this.scenario = scenario;
    this.warnings = warnings;
  }

  static clone(metadata: CardMetadata): CardMetadata {
    return new CardMetadata(CardSource.clone(metadata.source),
      TestScenario.clone(metadata.scenario), metadata.warnings.slice());
  }

  get isEmpty(): boolean {
    return this.source.isEmpty && this.scenario.category.length === 0 &&
      this.scenario.purpose.length === 0 && this.scenario.expectedResult.length === 0 &&
      this.scenario.conditions.length === 0 && this.scenario.amount.length === 0 &&
      this.scenario.legacyPdol.length === 0 && this.scenario.legacyCdol.length === 0 &&
      this.warnings.length === 0;
  }
}
