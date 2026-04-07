---
name: tax-law-pack
description: 연말정산 세법 팩을 작성할 때 사용한다. 2025 귀속 / 2026 신고 기준의 세율, 공제 한도, 인적공제 요건, 가족 지출 합산 규칙, 증빙 요구사항을 공식 출처와 함께 정리하고 tax pack contract를 만족시켜야 하면 반드시 사용한다.
---

# Tax Law Pack

이 스킬은 Agent A가 공식 세법 팩을 작성할 때 쓴다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `plugins/year-end-harness/context/tax-year-context.json`, `plugins/year-end-harness/agents/tax-expert.md`를 읽는다.
2. `plugins/year-end-harness/contracts/tax-pack-contract.md`와 `plugins/year-end-harness/templates/agent-a-tax-pack.md`를 연다.
3. [`references/source-checklist.md`](./references/source-checklist.md)를 따라 공식 출처만 수집한다.

## Output

- `.local/harness/<date>/<run-id>/agent-a-tax-pack.md`

## Rules

- 수치, 한도, 요건은 `confirmed`, `inferred`, `open-questions`로 분리한다.
- 모든 표에는 출처명, URL, 발행일, 효력일, 확인일을 남긴다.
- 가족 합산 가능 여부는 항목별로 분리한다.
- 코드 변경이나 구현 제안은 하지 않는다.
