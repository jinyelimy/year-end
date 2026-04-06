---
name: family-mapping-rules
description: 부양가족 식별, 지출 소유자 판정, 공제 청구자 결정 규칙을 설계하거나 구현할 때 사용한다. PDF 라인에서 가족별 지출을 분리하고 claimability 여부를 항목별로 판정하면서 family mapping contract를 만족시켜야 하면 반드시 사용한다.
---

# Family Mapping Rules

이 스킬은 Agent B와 Agent C가 가족 매핑 규칙을 설계하거나 구현할 때 쓴다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `docs/analysis/project-analysis.md`를 읽는다.
2. Agent A 세법 팩이 있으면 먼저 확인한다.
3. `plugins/year-end-harness/contracts/family-mapping-contract.md`와 `plugins/year-end-harness/contracts/architecture-pack-contract.md`를 먼저 고정한다.
4. [`references/mapping-contract.md`](./references/mapping-contract.md)는 보조 요약으로만 쓴다.

## Required Fields

- `person`
- `page`
- `rawLine`
- `ownerPersonKey`
- `claimantDependentId`
- `mappingConfidence`
- `mappingReason`
- `claimability`
- `claimabilityReason`
- `evidenceRequirementCode`
- `evidenceStatus`

## Rules

- 이름과 생년월일이 모두 맞을 때만 자동 매칭한다.
- 주민번호 앞자리나 관계 문구는 보조 증거로만 쓴다.
- 확신이 낮으면 `needs_review`로 남기고 자동 합산하지 않는다.
- 소유자와 공제 청구자를 반드시 구분한다.
