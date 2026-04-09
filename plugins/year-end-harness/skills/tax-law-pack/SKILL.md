---
name: tax-law-pack
description: 연말정산 세법 팩을 작성하거나 월별 공식 소스 스냅샷을 정규화 룰팩으로 변환할 때 사용한다. 2025 귀속 / 2026 신고 기준의 세율, 공제 한도, 인적공제 요건, 가족 지출 합산 규칙, 증빙 요구사항, 효력 시작일/종료일을 공식 출처와 함께 정리하고 `agent-a-tax-pack.md`, `source-manifest.json`, `normalized-rule-pack.json`을 만들어야 하면 반드시 사용한다.
---

# Tax Law Pack

이 스킬은 Agent A가 공식 세법 팩과 정규화 룰팩을 작성할 때 쓴다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `plugins/year-end-harness/context/tax-year-context.json`, `plugins/year-end-harness/agents/tax-expert.md`를 읽는다.
2. `plugins/year-end-harness/contracts/tax-pack-contract.md`, `plugins/year-end-harness/contracts/source-manifest-contract.md`, `plugins/year-end-harness/contracts/normalized-rule-pack-contract.md`, `plugins/year-end-harness/contracts/rule-diff-contract.md`, `plugins/year-end-harness/templates/agent-a-tax-pack.md`, `plugins/year-end-harness/templates/source-manifest.template.json`, `plugins/year-end-harness/templates/normalized-rule-pack.template.json`, `plugins/year-end-harness/templates/diff-from-previous.md`를 연다.
3. [`references/source-checklist.md`](./references/source-checklist.md)를 따라 공식 출처만 수집한다.
4. 수집한 원문을 source manifest 로 등록하고 이전 월 버전과 diff 를 만든다.
5. 계산에 필요한 숫자와 효력일만 `normalized-rule-pack.json`으로 정규화한다.

## Output

- `.local/harness/<date>/<run-id>/agent-a-tax-pack.md`
- `.local/harness/<date>/<run-id>/source-manifest.json`
- `.local/harness/<date>/<run-id>/normalized-rule-pack.json`
- `.local/harness/<date>/<run-id>/diff-from-previous.md`

## Rules

- 수치, 한도, 요건은 `confirmed`, `inferred`, `open-questions`로 분리한다.
- 모든 표에는 출처명, URL, 발행일, 효력일, 확인일을 남긴다.
- 가족 합산 가능 여부는 항목별로 분리한다.
- 각 정규화 규칙은 `sourceRefs`, `effectiveFrom`, `effectiveTo`, `confidence`를 가져야 한다.
- Markdown 세법 팩은 사람이 읽는 문서이고, `normalized-rule-pack.json`은 review/publish 후보다.
- review 를 통과한 월 버전만 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/` 정본 경로로 승격 대상이 된다.
- 자동 수집은 가능하지만 자동 publish 판정은 하지 않는다.
- 코드 변경이나 구현 제안은 하지 않는다.
