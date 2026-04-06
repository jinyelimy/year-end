---
name: year-end-a2a-orchestrator
description: 연말정산 A2A 하네스를 설계, 실행, 개선할 때 사용한다. 세법 검증, 가족 매핑 아키텍처, 구현, 저장소 검증, 3회 SDET 루프, 최종 QA를 5개 에이전트 흐름으로 조율해야 하거나 "하네스", "A2A", "에이전트 팀", "연말정산 워크플로"를 설계/운영하라는 요청에 반드시 사용한다.
---

# Year-End A2A Orchestrator

이 스킬은 연말정산 프로젝트의 5개 전문 에이전트와 저장소 검증 단계를 순차 또는 Agent Team 방식으로 조율한다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `docs/analysis/project-analysis.md`, `plugins/year-end-harness/README.md`를 읽는다.
2. `plugins/year-end-harness/context/tax-year-context.json`으로 기준 시점과 세법 컨텍스트를 확인한다.
3. 세법 값이 필요한 작업이면 [`official-source-policy.md`](./references/official-source-policy.md)를 먼저 따른다.
4. 핸드오프 산출물 형식은 [`handoff-contract.md`](./references/handoff-contract.md)를 따른다.

## Workflow

### Phase 0. Audit

- 현재 구현 범위, 미구현 공제 타입, 테스트 상태를 확인한다.
- 실행 범위를 사용자 요구와 저장소 경계에 맞춘다.

### Phase 1. Agent A

- `plugins/year-end-harness/agents/tax-expert.md`와 `skills/tax-law-pack/` 계약을 기준으로 세법 팩을 작성시킨다.
- 출처 링크, 발행일, 효력일, `confirmed/inferred` 분리가 없으면 통과시키지 않는다.

### Phase 2. Agent B

- `plugins/year-end-harness/agents/system-designer.md`와 `skills/family-mapping-rules/` 계약을 기준으로 DB 스키마, 가족 매핑 로직 트리, 증빙 검증 흐름을 설계시킨다.
- 소유자와 공제 청구자를 분리했는지 확인한다.

### Phase 3. Agent C

- `plugins/year-end-harness/agents/fullstack-developer.md` 기준으로 허용 범위 안에서 구현을 맡긴다.
- 구현 노트와 변경 파일을 남기게 한다.

### Phase 3.5. Repo Validation

- `skills/repo-validation/`을 사용해 parser tests, backend regression, frontend build 중 필요한 조합을 선택한다.
- backend 검증은 병렬 실행하지 않는다.

### Phase 4. Agent D x 3

- `plugins/year-end-harness/agents/sdet-loop.md`와 `skills/verification-loop/` 계약을 기준으로 `sdet-loop`를 3회 반복한다.
- 각 루프는 `테스트 -> 결함 보고 -> 수정 요청 -> 재검증`을 모두 포함해야 한다.
- `docs/samples/scenarios/` fixture를 우선 사용한다.

### Phase 5. Agent E

- `plugins/year-end-harness/agents/qa-verifier.md` 기준으로 `qa-verifier`가 최종 승인 또는 반려를 결정한다.
- 3회 루프 미완료, 미해결 blocking defect, 미확정 세법이 있으면 반려한다.

## Execution Mode

- Agent Team 기능이 있으면 에이전트를 병렬로 배치하되, 의존성이 있는 위상은 순서를 지킨다.
- Agent Team 기능이 없으면 같은 계약을 단일 세션에서 순차적으로 흉내 낸다.
- 어떤 방식이든 산출물, 검증, 게이트는 동일해야 한다.

## Output Discipline

- 중간 산출물은 `.local/harness/<date>/`에 둔다.
- 최종 요약은 저장소 문서와 변경 파일 경로를 함께 남긴다.
- 모든 단계는 `=== HARNESS RESULT ===` 블록으로 끝낸다.
