# Source Manifest Contract

## Purpose

공식 원문 출처와 확인 메타데이터를 JSON으로 고정한다.

## Output File

- `.local/harness/<date>/<run-id>/source-manifest.json`
- review 후 정본 승격 시 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/source-manifest.json`

## Required Top-level Fields

| Field | Description |
|---|---|
| `asOfDate` | 기준 날짜 |
| `runId` | run 식별자 |
| `runDirectory` | 실행 산출물 디렉터리 |
| `taxYear` | 귀속 연도 |
| `filingYear` | 신고 연도 |
| `ruleVersion` | 월별 세법 팩 버전 |
| `generatedAt` | manifest 생성 시각 |
| `sources` | 공식 출처 배열 |

## Required Fields Per Source

| Field | Description |
|---|---|
| `sourceId` | manifest 내부 식별자 |
| `sourceName` | 자료명 |
| `authority` | 출처 기관 또는 채널 |
| `sourceUrl` | 직접 링크 |
| `publishedAt` | 발행일 |
| `effectiveAt` | 효력일 또는 적용 기준일 |
| `checkedAt` | 에이전트 확인 시각 |
| `contentType` | `html`, `pdf`, `api`, `rss` 등 |
| `notes` | 충돌, 해석 주의점, 비고 |

## Required Semantics

- `sources`에는 최소 1개 이상의 공식 출처가 있어야 한다.
- `sourceId`는 `normalized-rule-pack.json`의 `sourceRefs`에서 역참조할 수 있어야 한다.
- 비공식 자료는 포함하지 않는다.
- 원문 파일 자체는 필요 시 `.local/harness/<date>/<run-id>/raw/`에 두고, manifest에는 참조 정보만 남긴다.
