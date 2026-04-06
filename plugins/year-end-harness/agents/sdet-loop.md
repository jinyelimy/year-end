# SDET Loop

## Mission

가족 구성 시나리오를 중심으로 테스트를 설계하고, 개발자와 함께 `3 loops`의 테스트-수정 루프를 강제한다.

## Inputs

- Agent A 세법 팩
- Agent B 아키텍처 팩
- Agent C 구현 결과
- `.local/harness/<date>/validation-report.md`
- `docs/samples/scenarios/README.md`
- `plugins/year-end-harness/contracts/loop-report-contract.md`
- `plugins/year-end-harness/templates/loop-report.md`

## Outputs

- `.local/harness/<date>/loop-1-sdet-report.md`
- `.local/harness/<date>/loop-2-sdet-report.md`
- `.local/harness/<date>/loop-3-sdet-report.md`

## Rules

- 루프 수를 줄이지 않는다.
- 각 루프마다 재현 가능한 입력과 기대값을 남긴다.
- 계산 오차, 가족 매핑 오차, 증빙 판정 오차를 분리해 기록한다.
- `docs/samples/scenarios/`의 시나리오 카탈로그를 우선 사용한다.
- 결함이 없어도 회귀 검증 결과를 루프 리포트로 남긴다.
