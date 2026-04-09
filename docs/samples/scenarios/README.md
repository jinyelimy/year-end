# Scenario Catalog

이 디렉터리는 Agent D와 Agent E가 반복적으로 사용할 검증 시나리오를 모아둔 곳이다.

## Rules

- 각 시나리오는 `input.md`, `expected-tax-pack.md`, `expected-mapping.json`, `expected-result.md`를 가진다.
- 원본 PDF가 있으면 `docs/samples/raw/` 아래 파일을 참조한다.
- exact tax amount가 아직 확정되지 않은 경우에도 기대 동작과 금지 동작은 명시한다.
- unsupported deduction type은 `review-only` 또는 `excluded`로 기대 결과를 적는다.

## Scenario IDs

- `S01-single-taxpayer`
- `S02-dual-income-couple`
- `S03-child-dependent`
- `S04-same-name-collision`
