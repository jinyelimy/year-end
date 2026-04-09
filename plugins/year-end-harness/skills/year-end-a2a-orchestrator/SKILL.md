---
name: year-end-a2a-orchestrator
description: 연말정산 A2A 하네스를 설계, 실행, 개선할 때 사용한다. 공식 소스 수집, 월별 세법팩 생성, 정규화 룰팩 작성, publish 흐름 설계, 가족 매핑 아키텍처, 구현, 저장소 검증, 3회 SDET 루프, 최종 QA를 5개 에이전트 흐름으로 조율해야 하거나 "하네스", "A2A", "에이전트 팀", "연말정산 워크플로", "세법팩", "rule pack"을 설계/운영하라는 요청에 반드시 사용한다.
---

# Year-End A2A Orchestrator

이 스킬은 연말정산 프로젝트의 5개 전문 에이전트와 저장소 검증 단계를 순차 또는 Agent Team 방식으로 조율한다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, `docs/analysis/project-analysis.md`, `plugins/year-end-harness/README.md`를 읽는다.
2. `plugins/year-end-harness/context/tax-year-context.json`으로 기준 시점과 세법 컨텍스트를 확인한다.
3. 세법 값이 필요한 작업이면 [`official-source-policy.md`](./references/official-source-policy.md)를 먼저 따른다.
4. 핸드오프 산출물 형식은 [`handoff-contract.md`](./references/handoff-contract.md)를 따른다.
5. 사용자가 별도 지시하지 않으면 현재 범위를 `환급/징수 계산 모듈`로 고정하고, 회사 제출 워크플로와 공식 신고서 생성은 후속 확장으로 본다.
6. 메인 사용자 흐름은 `홈택스 간소화 PDF 업로드 -> 공제 후보 추출 -> 본인/부양가족 매핑 -> deduction_items 등록 -> 증빙자료 동기화 -> 계산 -> 결과확인`으로 보고, 직접 입력은 fallback/보완 경로로 취급한다.
7. 세법 데이터 흐름은 `공식 소스 수집 -> 월별 세법팩 -> normalized-rule-pack.json -> review -> Git 정본 승격 -> PUBLISHED 게시 -> 계산`으로 본다.

## Workflow

### Phase 0. Audit

- 현재 구현 범위, 미구현 공제 타입, 테스트 상태를 확인한다.
- 실행 범위를 사용자 요구와 저장소 경계에 맞춘다.
- 현재 run이 `계산 모듈 본체`인지 `후속 통합 확장`인지 먼저 고정한다.
- 베이스라인을 `기본정보/부양가족/소득 입력`, `파싱`, `공제 항목 등록`, `부양가족 매칭`, `증빙자료`, `결과 계산`, `세법팩/룰셋`으로 나눠 점검한다.
- 실제 샘플 PDF 기준으로 메인 사용자 흐름이 어디까지 동작하는지 확인한다. 최소한 `docs/samples/고길동(750101)-2025년도자료.pdf`를 포함해 섹션 인식, 사람 식별, 금액 추출, 후보 등록 상태를 기록한다.
- 공제 항목별로 `없음`, `타입만 있음`, `파싱만 있음`, `등록 가능`, `매칭 가능`, `증빙 연결됨`, `계산 연결됨`, `UI 반영됨` 상태를 정리한다.
- 계산 엔진은 별도로 truth audit를 수행해 근로소득공제, 인적공제, 누진세율, 세액감면 반영 상태를 기록한다.
- 세법팩 축에서는 `ruleVersion` 형식, `DeductionRule` 실제 사용 여부, 결과 저장 시 스냅샷 추적 여부를 기록한다.

### Phase 1. Agent A

- `plugins/year-end-harness/agents/tax-expert.md`와 `skills/tax-law-pack/` 계약을 기준으로 세법 팩을 작성시킨다.
- `source-manifest.json`, `agent-a-tax-pack.md`, `normalized-rule-pack.json`, `diff-from-previous.md`를 함께 만들게 한다.
- 출처 링크, 발행일, 효력일, `confirmed/inferred` 분리가 없으면 통과시키지 않는다.
- 월별 `ruleVersion` 결정 근거와 이전 버전 diff가 없으면 통과시키지 않는다.
- review 를 통과한 월 버전만 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/` 정본 경로로 승격 가능하게 한다.

### Phase 2. Agent B

- `plugins/year-end-harness/agents/system-designer.md`와 `skills/family-mapping-rules/` 계약을 기준으로 DB 스키마, rule set publish, 파싱 후보 등록, 가족 매핑 로직 트리, 증빙 검증, 결과 계산 흐름을 설계시킨다.
- 소유자와 공제 청구자를 분리했는지 확인한다.
- 공제 후보가 `deduction_items`에 어떤 메타데이터로 등록되는지와 document checklist 동기화 규칙을 명시하게 한다.
- 메인 경로는 PDF 업로드 이후 자동 등록으로 두고, 직접 입력은 간소화자료 누락 또는 보정용 fallback으로 분리되게 한다.
- 결과확인 화면과 API 출력이 나중에 인사솔루션에 붙을 수 있도록 입력/출력 계약을 분리해 두게 한다.
- `tax_rule_sets`, `deduction_rules`, `RuleSetResolver`, `ruleSnapshotHash`, `READY_FOR_REVIEW -> PUBLISHED` 상태 전이를 포함시키게 한다.

### Phase 3. Agent C

- `plugins/year-end-harness/agents/fullstack-developer.md` 기준으로 허용 범위 안에서 구현을 맡긴다.
- 구현 노트와 변경 파일을 남기게 한다.
- 한 번의 run은 하나의 수직 슬라이스만 구현하게 한다.
  - `파싱/등록`
  - `부양가족 매칭`
  - `증빙자료`
  - `결과 계산`
  - `특정 공제 항목`
  - `세법팩/룰셋`

### Phase 3.5. Repo Validation

- `skills/repo-validation/`을 사용해 parser tests, backend regression, frontend build 중 필요한 조합을 선택한다.
- backend 검증은 병렬 실행하지 않는다.
- validation report 작성 뒤 `run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase validation`을 통과시킨다.
- 세법팩 연결 작업이면 이전 버전 회귀와 `ruleVersion` 일관성 점검을 결과에 포함시킨다.

### Phase 4. Agent D x 3

- `plugins/year-end-harness/agents/sdet-loop.md`와 `skills/verification-loop/` 계약을 기준으로 `sdet-loop`를 3회 반복한다.
- 각 루프는 `테스트 -> 결함 보고 -> 수정 요청 -> 재검증`을 모두 포함해야 한다.
- `docs/samples/scenarios/` fixture를 우선 사용한다.
- 각 루프는 파싱, 공제 항목 등록, 가족 매칭, 증빙자료, 결과 계산까지 묶어서 본다.
- 세법팩 관련 run에서는 월별 버전 diff, 효력일 경계, snapshot hash 재현성도 함께 본다.

### Phase 5. Agent E

- `plugins/year-end-harness/agents/qa-verifier.md` 기준으로 `qa-verifier`가 최종 승인 또는 반려를 결정한다.
- 3회 루프 미완료, 미해결 blocking defect, 미확정 세법이 있으면 반려한다.
- publish candidate 가 사람이 검토한 공식 소스와 연결되는지도 확인한다.
- 승인 또는 반려를 확정하기 전 `run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase final`을 통과시킨다.

## Execution Mode

- Agent Team 기능이 있으면 에이전트를 병렬로 배치하되, 의존성이 있는 위상은 순서를 지킨다.
- Agent Team 기능이 없으면 같은 계약을 단일 세션에서 순차적으로 흉내 낸다.
- 어떤 방식이든 산출물, 검증, 게이트는 동일해야 한다.

## Output Discipline

- 중간 산출물은 `.local/harness/<date>/<run-id>/`에 둔다.
- 같은 날짜에 다시 실행해도 새 `run-id`를 발급해 기존 run을 덮어쓰지 않는다.
- 월별 공식 세법팩은 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/`에도 정리해 둔다.
- 최종 요약은 저장소 문서와 변경 파일 경로를 함께 남긴다.
- 모든 단계는 `=== HARNESS RESULT ===` 블록으로 끝낸다.

## Prompting Pattern

- `audit run`: 현재 구현 상태와 갭을 정리하는 요청
- `law-pack run`: 공식 소스를 월별 세법팩과 정규화 룰팩으로 만드는 요청
- `design run`: 특정 슬라이스를 Phase 0~2까지만 수행하는 요청
- `implementation run`: 특정 슬라이스를 Phase 3~3.5까지만 수행하는 요청
- `qa run`: 특정 슬라이스 또는 전체 흐름을 Phase 4~5로 검증하는 요청

현재 제품 범위는 기본적으로 `환급/징수 계산 모듈`이다. 따라서 별도 요청이 없으면 아래 항목은 구현 범위에서 제외한다.

- 국세청 공식 제출서류 생성
- 회사 제출/승인 워크플로
- 회사별 인사솔루션 화면/정책 커스터마이징
- 급여 반영 및 환급 지급 프로세스

대신 아래 항목은 현재 범위 안에 포함한다.

- 홈택스 간소화 PDF 업로드와 자동 공제 등록
- 결과확인 화면 계산 근거
- 외부 시스템 재사용을 위한 계산 입력/출력 계약
- 공제 항목, 가족 매칭, 증빙자료, 계산 결과의 구조화된 메타데이터 보존
- 월별 세법팩, 정규화 룰팩, publish 기반 계산 재현성

대형 범위는 아래처럼 쪼개는 것을 기본으로 한다.

1. `세법팩/룰셋 audit`
2. `월별 세법팩 생성`
3. `정규화 룰팩 검토`
4. `ruleVersion 정리`
5. `RuleSetResolver`
6. `PDF 파싱 공통 기반`
7. `가족 매핑 계약 필드`
8. `세액 계산 엔진 공통 기반`
9. `결과확인 출력 계약`
10. `기부금`
11. `신용카드 계산 연결`
