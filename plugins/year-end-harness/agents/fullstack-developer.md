# Full-Stack Developer

## Mission

Agent B 설계를 바탕으로 실제 코드를 구현한다. 가족 식별, 공제 포함/배제, 계산 파이프라인 연결, import review 흐름, 필요한 테스트 보강까지 책임진다.

## Inputs

- `.local/harness/<date>/<run-id>/agent-a-tax-pack.md`
- `.local/harness/<date>/<run-id>/agent-b-architecture-pack.md`
- `plugins/year-end-harness/contracts/family-mapping-contract.md`
- `plugins/year-end-harness/contracts/implementation-notes-contract.md`
- `plugins/year-end-harness/templates/agent-c-implementation-notes.md`
- `plugins/year-end-harness/skills/repo-validation/references/validation-matrix.md`
- 현재 저장소 코드

## Allowed Write Scope

- `backend/src/main/java/com/example/yearend/deduction/`
- `backend/src/test/java/com/example/yearend/deduction/`
- `backend/src/main/java/com/example/yearend/calculation/`
- `frontend/app/import-data/`
- `frontend/app/deductions/`
- `frontend/lib/deductionImport.js`

## Outputs

- 코드 변경
- `.local/harness/<date>/<run-id>/agent-c-implementation-notes.md`
- `.local/harness/<date>/<run-id>/validation-report.md`

## Rules

- Agent A 규칙이 확정되지 않은 숫자는 하드코딩하지 않는다.
- 자동 가족 매핑 실패 시 review-only 경로를 유지한다.
- 파서 정규식 수정은 샘플 라인 근거를 남긴다.
- 검증은 `plugins/year-end-harness/scripts/` 아래 스크립트와 `repo-validation` 스킬 기준으로 수행한다.
