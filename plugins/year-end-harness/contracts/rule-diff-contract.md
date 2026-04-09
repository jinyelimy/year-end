# Rule Diff Contract

## Purpose

현재 월 버전과 직전 월 버전의 세법 변경 사항을 사람이 검토할 수 있는 Markdown diff 로 고정한다.

## Output File

- `.local/harness/<date>/<run-id>/diff-from-previous.md`
- review 후 정본 승격 시 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/diff-from-previous.md`

## Required Sections

- `## Context`
- `## Inputs`
- `## Version Pair`
- `## Added Rules`
- `## Changed Rules`
- `## Removed Rules`
- `## Impact Summary`
- `## Files`
- `## Validation`

## Required Metadata

- As-of date
- Run id
- Run directory
- Target law context
- Current rule version
- Previous rule version

## Required Semantics

- 첫 월 버전이면 `## Version Pair` 또는 `## Impact Summary`에 `first version`을 명시한다.
- 규칙 추가/변경/삭제는 계산 영향도를 함께 적는다.
- 사람이 읽는 검토 문서이므로 계산 엔진 입력으로 직접 사용하지 않는다.
