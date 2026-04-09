# Tax Pack Contract

## Purpose

Agent A 세법 팩의 필수 구조와 메타데이터를 고정한다. 이 계약은 사람이 읽는 Markdown 근거 문서에 대한 것이며, 계산용 JSON 구조는 `normalized-rule-pack-contract.md`가 따로 담당한다.

## Required Sections

- `## Context`
- `## Inputs`
- `## Source Register`
- `## Confirmed Rules`
- `## Inferred Rules`
- `## Open Questions`
- `## Files`
- `## Validation`

## Required Metadata

모든 공식 출처 행은 아래 필드를 포함한다.

| Field | Description |
|---|---|
| `sourceName` | 자료명 |
| `sourceUrl` | 직접 링크 |
| `publishedAt` | 발행일 |
| `effectiveAt` | 효력일 또는 적용 기준일 |
| `checkedAt` | 에이전트 확인 시각 |
| `notes` | 충돌, 해석 주의점 |

## Required Subsections Under Confirmed Rules

- `### 세율표`
- `### 공제 한도표`
- `### 인적공제 판단표`
- `### 항목별 가족 합산 가능 여부 표`
- `### 증빙 요구사항 표`

## Output File

- `.local/harness/<date>/<run-id>/agent-a-tax-pack.md`
- `.local/harness/<date>/<run-id>/source-manifest.json`
- `.local/harness/<date>/<run-id>/normalized-rule-pack.json`
- `.local/harness/<date>/<run-id>/diff-from-previous.md`

## Required Output Semantics

- `agent-a-tax-pack.md`는 사람이 읽는 근거 문서다.
- `source-manifest.json`은 원문 스냅샷과 출처 레지스트리를 담는다.
- `normalized-rule-pack.json`은 `DRAFT` 또는 `READY_FOR_REVIEW` 상태의 계산 publish 후보이며, 각 규칙의 효력일과 출처 참조를 포함해야 한다.
- `diff-from-previous.md`는 이전 월 버전과의 차이를 설명한다. 첫 월 버전이면 `first version`이라고 명시한다.
- review 를 통과한 정본은 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/`에 승격한다.

## Result Block

Markdown 산출물은 아래 블록으로 끝낸다. JSON 산출물은 구조 검증으로 대체한다.

```text
=== HARNESS RESULT ===
STATUS   : success | warning | error
SUMMARY  : <한 줄 요약>
ARTIFACTS: <파일 경로>
NEXT     : <다음 권고 액션>
======================
```
