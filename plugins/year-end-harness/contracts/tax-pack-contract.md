# Tax Pack Contract

## Purpose

Agent A 세법 팩의 필수 구조와 메타데이터를 고정한다.

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

## Result Block

모든 산출물은 아래 블록으로 끝낸다.

```text
=== HARNESS RESULT ===
STATUS   : success | warning | error
SUMMARY  : <한 줄 요약>
ARTIFACTS: <파일 경로>
NEXT     : <다음 권고 액션>
======================
```
