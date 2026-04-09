# Normalized Rule Pack Contract

## Purpose

월별 공식 세법팩을 계산 엔진이 읽을 수 있는 정규화 JSON 구조로 고정한다.

## Output File

- `.local/harness/<date>/<run-id>/normalized-rule-pack.json`
- review 후 정본 승격 시 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/normalized-rule-pack.json`

## Required Top-level Fields

| Field | Description |
|---|---|
| `ruleSetId` | 계산 publish 대상 룰셋 식별자 |
| `taxYear` | 귀속 연도 |
| `filingYear` | 신고 연도 |
| `ruleVersion` | 월별 버전 문자열 |
| `status` | `DRAFT`, `READY_FOR_REVIEW`, `PUBLISHED`, `RETIRED` 중 하나 |
| `generatedAt` | 생성 시각 |
| `generatedFrom` | source manifest, upstream pack, diff 기준 등 생성 근거 |
| `rules` | 계산용 규칙 배열 |

## Required Fields Per Rule

| Field | Description |
|---|---|
| `deductionType` | 공제 타입 |
| `subType` | 세부 분류, 없으면 `null` 허용 |
| `ruleCode` | 규칙 코드 |
| `ruleCategory` | `LIMIT`, `RATE`, `THRESHOLD`, `ELIGIBILITY`, `TAX_CREDIT`, `DEDUCTION`, `BRACKET` 등 |
| `parameters` | 계산에 쓰는 숫자 파라미터 객체 |
| `effectiveFrom` | 효력 시작일 |
| `effectiveTo` | 효력 종료일 또는 `null` |
| `sourceRefs` | 출처 레퍼런스 배열 |
| `confidence` | `confirmed` 또는 `inferred` |

## Required Semantics

- 계산 엔진은 Markdown tax pack 대신 이 JSON 기반의 `PUBLISHED` 룰셋을 읽는다.
- 모든 숫자 파라미터는 출처를 역추적할 수 있어야 한다.
- `confidence=inferred`인 규칙은 review 없이 publish 하지 않는다.
- 같은 `ruleCode`가 중복되면 효력일 구간이 겹치지 않아야 한다.
- 규칙별 `effectiveFrom`, `effectiveTo`는 월 버전보다 우선한다.
- `READY_FOR_REVIEW` 상태의 후보 JSON을 계산 엔진에 직접 연결하지 않는다.
