---
name: repo-validation
description: 연말정산 저장소에서 코드 변경 이후 어떤 검증 명령을 돌릴지 결정하고 실행할 때 사용한다. 파서 테스트, 전체 백엔드 회귀, 프론트 빌드 중 필요한 조합을 선택하고 validation report를 남겨야 하면 반드시 사용한다.
---

# Repo Validation

이 스킬은 Agent C 이후 단계에서 저장소 검증을 일관되게 수행할 때 쓴다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `plugins/year-end-harness/context/tax-year-context.json`을 읽는다.
2. [`references/validation-matrix.md`](./references/validation-matrix.md)를 따라 변경 범위별 명령을 고른다.
3. validation 결과는 `plugins/year-end-harness/templates/validation-report.md`와 `plugins/year-end-harness/contracts/validation-report-contract.md` 형식으로 남긴다.

## Rules

- Windows에서는 `gradlew.bat`, `npm.cmd`를 사용한다.
- 백엔드 검증은 같은 `backend/build` 디렉터리에서 병렬 실행하지 않는다.
- 파서만 바뀌었으면 parser tests를 우선한다.
- 계산 엔진이 바뀌면 full backend regression까지 올린다.
- 프론트 파일이 바뀌면 `run-frontend-build.cmd`를 반드시 포함한다.

## Output

- `.local/harness/<date>/validation-report.md`
