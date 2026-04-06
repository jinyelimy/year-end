# 연말정산 A2A 하네스 설계

업데이트: 2026-04-06

## 1. 목표

이 하네스는 국세청 연말정산 간소화 PDF 파싱, 공제 항목 분류, 부양가족 매핑, 세액 계산, 증빙 검증을 하나의 Agent-to-Agent 흐름으로 통제하기 위한 Codex 기준 설계다. 최종 목적은 다음 요구를 안정적으로 구현하고, 같은 흐름을 반복 실행해도 같은 산출물과 검증 게이트를 만들도록 하는 것이다.

- PDF 파싱 무결성: 텍스트, 표, 메타데이터를 누락 없이 수집한다.
- 공제 항목 분류: 보험료, 주택자금, 개인연금저축, 소기업/소상공인공제, 주택마련저축공제, 투자조합출자, 우리사주조합출연금, 장기집합투자증권/청년형 저축공제, 세액감면 및 기타세액공제, 의료비, 교육비, 기부금, 신용카드 등을 정확히 매핑한다.
- 세액 계산 파이프라인: 총급여액 -> 근로소득 금액 -> 근로소득 과세표준 -> 산출세액 -> 결정세액 -> 납부/환급 세액 순으로 계산한다.
- 부양가족 매핑 및 검증: 가족별 나이/소득 요건과 항목별 합산 가능 여부를 판정한다.
- 증빙자료 로직 강화: 공제 대상, 가족 매핑, 계산 결과에 필요한 증빙 누락과 정합성 오류를 검증한다.

## 2. 기준 시점과 법령 버전 정책

- 기준일은 2026-04-06이다.
- 기본 서비스 타깃은 `2025 귀속 소득 / 2026 신고`다.
- 날짜와 세법 기본 컨텍스트는 `plugins/year-end-harness/context/tax-year-context.json`을 단일 소스로 삼는다.
- Agent A는 세법 값을 기억에 의존해 확정하지 않는다. 매 실행마다 공식 자료를 재검증하고, 출력물에 자료명, URL, 발행일, 효력일, 확인 시각을 남긴다.
- 공식 소스 우선순위는 `국세청 -> 국가법령정보센터 -> 기획재정부` 순서다.
- 공식 문서와 저장소 문서가 충돌하면 Agent A의 최신 공식 소스 팩이 우선이며, 충돌 항목을 별도 섹션으로 기록한다.

### 기본 확인 소스

- 국세청 연말정산 안내 자료
- 국가법령정보센터 `소득세법`, `소득세법 시행령`, `소득세법 시행규칙`
- 기획재정부 세법개정 보도자료 및 후속 시행령/시행규칙 자료
- 저장소 내 기준 문서: `docs/references/2025년 원천징수의무자를 위한 연말정산 신고안내.pdf`

## 3. Codex 하네스 토폴로지

이 하네스는 `AGENTS.md`를 최상위 작업 규칙으로 두고, `plugins/year-end-harness/`를 Codex 플러그인 실행체로 둔다.

### 핵심 구성

- `plugins/year-end-harness/agents/`: 역할 브리프
- `plugins/year-end-harness/skills/`: 실행 스킬
- `plugins/year-end-harness/contracts/`: 산출물 단일 계약 소스
- `plugins/year-end-harness/templates/`: 반복 실행용 템플릿
- `plugins/year-end-harness/scripts/`: 저장소 검증 스크립트
- `docs/samples/scenarios/`: Agent D/E용 scenario fixture

### 팀 구성

| 에이전트 | 역할 | 핵심 산출물 | 기본 쓰기 범위 |
|---|---|---|---|
| Agent A `tax-expert` | 2025 귀속/2026 신고 기준 최신 세법 팩 작성 | 세법 팩, 가족공제 판단표, 증빙 매트릭스 | `.local/harness/`, 필요 시 `docs/` 메모 |
| Agent B `system-designer` | 가족 매핑/계산/증빙 아키텍처 설계 | 아키텍처 팩, DB 스키마 제안, 로직 트리 | `.local/harness/`, 설계 문서 |
| Agent C `fullstack-developer` | 설계 기반 구현 | 코드 변경, 구현 노트, validation report | `backend/`, `frontend/` 허용 범위 |
| Agent D `sdet-loop` | 3회 테스트-수정 루프 강제 | 루프별 결함 보고서, 재현 케이스, 회귀 결과 | `backend/src/test/`, `frontend` 테스트, `.local/harness/` |
| Agent E `qa-verifier` | 최종 E2E 승인 | 최종 검증 리포트, 승인/반려 판정 | `.local/harness/`, 검증 문서 |

## 4. 계약 중심 설계 원칙

모든 단계는 문서형 설명이 아니라 계약과 템플릿을 함께 따른다.

### 계약 파일

- `contracts/tax-pack-contract.md`
- `contracts/architecture-pack-contract.md`
- `contracts/family-mapping-contract.md`
- `contracts/implementation-notes-contract.md`
- `contracts/validation-report-contract.md`
- `contracts/loop-report-contract.md`
- `contracts/final-verification-contract.md`

### 핵심 필드

가족 매핑에서는 아래 필드를 공통으로 쓴다.

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

## 5. 산출물 계약

모든 에이전트는 `.local/harness/` 아래에 날짜별 폴더를 만들어 중간 산출물을 남긴다.

```text
.local/harness/2026-04-06/
  agent-a-tax-pack.md
  agent-b-architecture-pack.md
  agent-c-implementation-notes.md
  validation-report.md
  loop-1-sdet-report.md
  loop-2-sdet-report.md
  loop-3-sdet-report.md
  agent-e-final-verification.md
```

모든 리포트는 아래 블록으로 끝낸다.

```text
=== HARNESS RESULT ===
STATUS   : success | warning | error
SUMMARY  : <한 줄 요약>
ARTIFACTS: <파일 경로>
NEXT     : <다음 권고 액션>
======================
```

## 6. 가족 매핑 설계 원칙

가족 매핑은 “PDF 행의 소유자”와 “공제를 청구할 납세자”를 분리해 처리한다.

### 로직 트리

1. PDF 행에서 `이름`, `생년월일`, `주민번호 앞자리`, `관계 문자열`, `원시 라인`을 최대한 추출한다.
2. `이름 + 생년월일`이 기존 가족 데이터와 일치하면 1차 매핑한다.
3. 동일 이름 다건이 있으면 `주민번호 앞자리`나 관계 힌트로 보정한다.
4. 확신이 부족하면 자동 합산하지 않고 `needs_review`로 보낸다.
5. 소유자가 확정되면 Agent A 규칙표를 사용해 항목별 합산 가능 여부를 판정한다.
6. 합산 가능이면 `claimant = taxpayer`, 불가하면 `owner = claimant` 또는 `excluded`로 기록한다.
7. 증빙 누락이면 계산 대상과 별도로 `evidence warning`을 남긴다.

## 7. 단계별 실행 흐름

### Phase 0. Context Audit

- `AGENTS.md`, `docs/analysis/project-analysis.md`, 본 문서, `plugins/year-end-harness/README.md`, `plugins/year-end-harness/context/tax-year-context.json`을 읽는다.
- 현재 구현 범위와 미구현 공제 타입을 확인한다.
- 기본 타깃 연도를 `2025 귀속 / 2026 신고`로 고정하고, 예외가 있으면 첫 메시지에서 명시한다.

### Phase 1. Agent A Tax Pack

- `tax-expert`와 `tax-law-pack` 스킬을 사용한다.
- `tax-pack-contract.md`와 `agent-a-tax-pack.md` 템플릿을 기준으로 작성한다.
- 공식 자료 수집, 가족 공제 요건 표, 항목별 가족 합산 규칙 표, 증빙 요구사항 표를 만든다.

게이트:

- 공식 출처 링크, 발행일, 효력일, 확인 시각이 있어야 한다.
- `confirmed`와 `inferred`가 분리돼 있어야 한다.

### Phase 2. Agent B Architecture Pack

- `system-designer`와 `family-mapping-rules` 스킬을 사용한다.
- `architecture-pack-contract.md`, `family-mapping-contract.md`, `agent-b-architecture-pack.md` 템플릿을 기준으로 작성한다.
- DB 스키마 초안, 파싱 -> 가족 식별 -> 공제 청구 판정 -> 계산 -> 증빙 확인 흐름, 리뷰 필요 상태와 자동 적용 상태의 분기를 설계한다.

게이트:

- 파싱 소유자와 공제 청구자 분리가 표현돼야 한다.
- 로직 트리가 항목별로 확장 가능해야 한다.

### Phase 3. Agent C Implementation

- `fullstack-developer`가 허용 경계 안에서만 구현한다.
- `implementation-notes-contract.md`와 `agent-c-implementation-notes.md` 템플릿으로 구현 노트를 남긴다.
- 계산 엔진과 PDF import, deduction review 흐름을 연결한다.

### Phase 3.5. Repo Validation

- `repo-validation` 스킬을 사용한다.
- parser tests, backend regression, frontend build 중 필요한 조합을 선택한다.
- Windows 기준 검증 스크립트는 아래를 사용한다.
  - `plugins\year-end-harness\scripts\run-parser-tests.cmd`
  - `plugins\year-end-harness\scripts\run-backend-tests.cmd`
  - `plugins\year-end-harness\scripts\run-frontend-build.cmd`
- backend 검증은 같은 `build/` 디렉터리를 공유하므로 병렬 실행하지 않는다.
- 결과는 `validation-report-contract.md`와 `validation-report.md` 템플릿으로 남긴다.

### Phase 4. Agent D 3 Loops

- `sdet-loop`와 `verification-loop` 스킬을 사용한다.
- `docs/samples/scenarios/` fixture를 우선 사용한다.
- Loop 1: 기본 가족 시나리오와 단일 납세자
- Loop 2: 맞벌이 부부, 자녀, 노부모 포함
- Loop 3: 경계값과 증빙 누락, 동일 이름/동년생 충돌

각 루프는 `테스트 -> 결함 보고 -> Agent C 수정 -> 재검증`을 완료해야 한다.

### Phase 5. Agent E Final Verification

- `qa-verifier`와 `verification-loop` 스킬을 사용한다.
- 다인가족 더미 PDF 또는 scenario fixture를 사용해 최종 납부/환급 계산과 증빙 판정을 검증한다.
- `final-verification-contract.md`와 `final-verification.md` 템플릿으로 결과를 남긴다.

## 8. 게이트와 중단 조건

- Agent A 산출물이 없으면 B와 C는 진행하지 않는다.
- Agent D의 3회 루프가 끝나지 않으면 E는 승인하지 않는다.
- `DONATION`, `CREDIT_CARD`처럼 미지원 정책이 남아 있으면 시뮬레이션 포함 여부를 명시적으로 차단하거나 경고해야 한다.
- 공식 소스가 충돌하거나 10% 이상 불확실성이 남으면 Agent A 단계로 되돌린다.
- validation report 없이 QA 단계로 넘어가지 않는다.

## 9. 저장소 반영 구조

커밋되는 하네스 파일은 아래에 둔다.

```text
docs/architecture/harness-engineering-design.md
docs/samples/scenarios/
plugins/year-end-harness/
.agents/plugins/marketplace.json
```

커밋하지 않는 실행 산출물은 아래에 둔다.

```text
.local/harness/
```

## 10. 안티패턴

- 최신 세법을 브라우징 없이 기억으로 확정하는 것
- PDF 샘플 라인 없이 정규식을 바꾸는 것
- 가족 소유자 식별 확신이 부족한데도 자동 합산하는 것
- backend 검증을 병렬로 돌려 Gradle 산출물을 충돌시키는 것
- Agent D의 3회 루프를 생략하는 것
- Agent E가 미해결 결함을 안고 승인하는 것
