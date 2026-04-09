# 연말정산 A2A 하네스 설계

업데이트: 2026-04-09

## 1. 목표

이 하네스는 국세청 연말정산 간소화 PDF 파싱, 공제 항목 분류, 부양가족 매핑, 세액 계산, 증빙 검증을 하나의 Agent-to-Agent 흐름으로 통제하기 위한 Codex 기준 설계다. 최종 목적은 다음 요구를 안정적으로 구현하고, 같은 흐름을 반복 실행해도 같은 산출물과 검증 게이트를 만들도록 하는 것이다.

- PDF 파싱 무결성: 텍스트, 표, 메타데이터를 누락 없이 수집한다.
- 공제 항목 분류: 보험료, 주택자금, 개인연금저축, 소기업/소상공인공제, 주택마련저축공제, 투자조합출자, 우리사주조합출연금, 장기집합투자증권/청년형 저축공제, 세액감면 및 기타세액공제, 의료비, 교육비, 기부금, 신용카드 등을 정확히 매핑한다.
- 세액 계산 파이프라인: 총급여액 -> 근로소득 금액 -> 근로소득 과세표준 -> 산출세액 -> 결정세액 -> 납부/환급 세액 순으로 계산한다.
- 부양가족 매핑 및 검증: 가족별 나이/소득 요건과 항목별 합산 가능 여부를 판정한다.
- 증빙자료 로직 강화: 공제 대상, 가족 매핑, 계산 결과에 필요한 증빙 누락과 정합성 오류를 검증한다.
- 세법 운영 안정성: 공식 소스를 월별 세법 팩으로 수집하고, 정규화 룰팩을 계산 엔진에 연결해 하드코딩 숫자 의존도를 줄인다.

## 1.5 현재 베이스라인과 하네스가 마저 완성해야 할 범위

이 저장소는 아래 영역까지는 이미 1차 구현이 진행된 상태를 전제로 한다.

- 기본정보 입력
- 부양가족 입력
- 소득 입력
- 홈택스 PDF 1차 파싱
- 일부 공제 항목의 가져오기/리뷰 UI

하네스의 다음 구현 범위는 단순히 "공제 정책 클래스 추가"가 아니라 아래의 제품 완성 흐름 전체다.

1. 홈택스 PDF에서 남은 공제 항목까지 파싱 후보를 추출한다.
2. 추출 후보를 `deduction_items`에 일관된 메타데이터와 함께 등록한다.
3. 등록된 항목을 부양가족/납세자 기준으로 안전하게 매칭한다.
4. 매칭 결과와 공제 가능 여부를 바탕으로 증빙자료 체크리스트를 동기화한다.
5. 계산 엔진이 총급여, 근로소득공제, 인적공제, 공제 한도, 공제율, 세액감면을 포함한 결과 계산을 수행한다.
6. 결과확인 화면이 위 계산과 동일한 근거를 사용자에게 보여준다.
7. 공식 소스를 월별 세법 팩과 정규화 룰팩으로 정리하고, 계산 엔진이 사람 검토를 거쳐 `PUBLISHED` 상태가 된 룰셋만 사용하게 만든다.

즉 앞으로의 하네스 구현 단위는 항상 아래 6개 축을 함께 본다. 세법 팩 단계는 기존 A2A phase를 대체하지 않고, 원래 흐름 사이에 `Phase 1 / Phase 1.5`로 끼워 넣는다.

- `세법 팩`
- `파싱`
- `공제 항목 등록`
- `부양가족 매칭`
- `증빙자료 동기화`
- `결과 계산`

특정 공제 항목을 "구현 완료"로 보려면 최소한 위 축 중 해당 항목에 필요한 구간이 설계와 코드에 모두 반영돼야 한다.

## 1.6 제품 범위 결정

현재 이 저장소가 우선 완성하려는 모듈은 "회사 제출용 연말정산 패키지"가 아니라, 사용자가 자신의 연말정산 예상 결과를 계산하고 확인하는 `환급/징수 계산 모듈`이다.

현재 제품의 메인 사용자 흐름은 아래와 같이 고정한다.

1. 홈택스 간소화 PDF를 업로드한다.
2. PDF 섹션을 인식하고 공제 후보를 추출한다.
3. 후보를 본인 또는 부양가족에게 안전하게 매핑한다.
4. 매핑 결과를 바탕으로 `deduction_items`와 증빙자료 체크리스트를 동기화한다.
5. 계산 엔진이 공제 가능 항목만 반영해 환급/징수 결과를 계산한다.
6. 결과확인 화면과 API가 같은 계산 근거를 보여준다.

현재 하네스의 제품 범위는 아래까지로 고정한다.

- 기본정보, 부양가족, 소득 입력
- 홈택스 간소화 PDF 파싱과 공제 항목 자동 등록
- 자동 반영 또는 review 기반의 부양가족 매칭
- 공제 항목별 증빙자료 체크리스트 동기화
- 결과확인 화면에서 환급/징수 예상액, 계산 근거, 한도, 공제율, 세액감면 반영 결과 표시
- 월별 세법 팩 생성, 정규화, review, publish 준비

직접 입력은 제품 범위에 포함되지만, 메인 경로가 아니라 아래 목적의 `fallback / 보완 입력`으로 본다.

- 간소화 자료에 없는 공제 항목 수기 입력
- 파싱 실패 또는 review-only 항목 보완
- 증빙자료 확인 후 보정 입력

현재 범위에서 제외하는 항목은 아래와 같다.

- 국세청 제출용 공식 신고서/부속서류 생성
- 회사 제출 워크플로, 회사 유형 1~5별 제출 절차 지원
- 원천징수영수증 발급, 회사 환급 지급 프로세스, 급여 시스템 반영
- 회사별 인사솔루션 화면/승인 흐름 커스터마이징

다만 이후 인사솔루션이나 외부 연말정산 모듈로 재사용할 수 있도록 아래 준비는 현재 하네스 범위 안에 포함한다.

- 계산 입력값과 결과값을 API/도메인 계약으로 정규화
- 공제 항목, 가족 매칭, 증빙자료, 계산 근거를 구조화된 메타데이터로 보존
- 결과확인 화면과 동일한 근거를 외부 시스템이 재사용할 수 있게 설계
- 회사 제출용 기능은 후속 확장 슬라이스로 분리 가능하게 경계를 유지
- 세법 팩 버전과 계산 스냅샷을 기준으로 과거 계산을 재현 가능하게 설계

## 2. 기준 시점과 법령 버전 정책

- 기준일은 2026-04-09다.
- 기본 서비스 타깃은 `2025 귀속 소득 / 2026 신고`다.
- 날짜와 세법 기본 컨텍스트는 `plugins/year-end-harness/context/tax-year-context.json`을 단일 소스로 삼는다.
- Agent A는 세법 값을 기억에 의존해 확정하지 않는다. 매 실행마다 공식 자료를 재검증하고, 출력물에 자료명, URL, 발행일, 효력일, 확인 시각을 남긴다.
- 공식 문서와 저장소 문서가 충돌하면 Agent A의 최신 공식 소스 팩이 우선이며, 충돌 항목을 별도 섹션으로 기록한다.

### 기본 확인 소스

- 국세청 연말정산 종합안내, 책자, Q&A
- 홈택스 연말정산 관련 공식 문서
- 국가법령정보센터 `소득세법`, `소득세법 시행령`, `소득세법 시행규칙`
- 법제처/국세청 법령해석 API
- 기획재정부 세법개정 보도자료 및 RSS
- 저장소 내 기준 문서: `docs/references/2025년 원천징수의무자를 위한 연말정산 신고안내.pdf`

### 공식 소스 우선순위

- `국세청/홈택스 -> 국가법령정보센터 -> 법령해석 -> 기획재정부`

### 월별 세법 팩 정책

- 월별 세법 팩 버전은 `YYYY.MM[.patch]` 형식을 기본으로 한다.
- 월별 세법 팩은 사람이 읽는 `agent-a-tax-pack.md`와 계산용 `normalized-rule-pack.json`을 함께 만든다.
- 계산은 월 버전만 보지 않고 각 규칙의 `effectiveFrom`, `effectiveTo`를 함께 본다.
- 자동 수집은 가능하지만 자동 publish 는 금지한다.
- `normalized-rule-pack.json`은 `DRAFT` 또는 `READY_FOR_REVIEW` 후보 문서로 취급하고, 런타임은 `PUBLISHED` 상태의 룰셋만 읽는다.
- 새 월 버전에는 이전 버전과의 `diff-from-previous.md`를 남긴다.

## 3. Codex 하네스 토폴로지

이 하네스는 `AGENTS.md`를 최상위 작업 규칙으로 두고, `plugins/year-end-harness/`를 Codex 플러그인 실행체로 둔다.

### 핵심 구성

- `plugins/year-end-harness/agents/`: 역할 브리프
- `plugins/year-end-harness/skills/`: 실행 스킬
- `plugins/year-end-harness/contracts/`: 산출물 단일 계약 소스
- `plugins/year-end-harness/templates/`: 반복 실행용 템플릿
- `plugins/year-end-harness/context/`: 날짜와 세법 컨텍스트
- `plugins/year-end-harness/law-packs/`: 월별 세법 팩 저장소
- `plugins/year-end-harness/scripts/`: 저장소 검증 스크립트
- `docs/samples/scenarios/`: Agent D/E용 scenario fixture

### 팀 구성

| 에이전트 | 역할 | 핵심 산출물 | 기본 쓰기 범위 |
|---|---|---|---|
| Agent A `tax-expert` | 2025 귀속/2026 신고 기준 최신 세법 팩 작성과 정규화 룰 후보 생성 | 세법 팩, source manifest, normalized rule pack, diff | `.local/harness/`, `plugins/year-end-harness/law-packs/` 초안 |
| Agent B `system-designer` | 가족 매핑/계산/증빙/룰 publish 아키텍처 설계 | 아키텍처 팩, DB 스키마 제안, resolver/loader 로직 트리 | `.local/harness/`, 설계 문서 |
| Agent C `fullstack-developer` | 설계 기반 구현 | 코드 변경, 구현 노트, validation report | `backend/`, `frontend/` 허용 범위 |
| Agent D `sdet-loop` | 3회 테스트-수정 루프 강제 | 루프별 결함 보고서, 재현 케이스, 회귀 결과 | `backend/src/test/`, `frontend` 테스트, `.local/harness/` |
| Agent E `qa-verifier` | 최종 E2E 승인 | 최종 검증 리포트, 승인/반려 판정 | `.local/harness/`, 검증 문서 |

## 4. 계약 중심 설계 원칙

모든 단계는 문서형 설명이 아니라 계약과 템플릿을 함께 따른다.

### 계약 파일

- `contracts/tax-pack-contract.md`
- `contracts/source-manifest-contract.md`
- `contracts/normalized-rule-pack-contract.md`
- `contracts/rule-diff-contract.md`
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

세법 팩에서는 아래 필드를 공통으로 쓴다.

- `ruleVersion`
- `ruleSetId`
- `ruleCode`
- `ruleCategory`
- `effectiveFrom`
- `effectiveTo`
- `sourceRefs`
- `confidence`

## 4.5 공제 항목 구현 완료 정의

공제 항목별 작업은 아래 체크포인트로 쪼개서 본다.

1. `도메인 정의`: `DeductionType`, subType, 저장 필드, UI 라벨
2. `세법 팩`: 공식 소스 수집, 한도/공제율/효력일/출처 정규화
3. `파싱`: PDF 섹션 인식, 후보 추출, 원시 라인/페이지/사람 정보 보존
4. `등록`: `deduction_items` 생성, `attributesJsonb` 메타데이터 저장, review 상태 초기화
5. `부양가족 매칭`: 소유자 식별, 청구자 판정, review fallback
6. `증빙`: `evidenceRequirementCode`, `evidenceStatus`, 체크리스트 반영
7. `계산`: 한도, 공제율, 계산 포함 여부, 결과 화면/요약 반영

하네스는 위 항목 중 빠진 구간이 있으면 해당 공제 항목을 "부분 구현"으로 취급한다.

## 5. 산출물 계약

모든 에이전트는 `.local/harness/` 아래에 `날짜/run-id` 폴더를 만들어 중간 산출물을 남긴다. 같은 날짜에 여러 번 실행해도 기존 run을 덮어쓰지 않도록 `run-id`를 매번 새로 발급한다.

```text
.local/harness/2026-04-09/20260409-154500-family-mapping/
  source-manifest.json
  agent-a-tax-pack.md
  normalized-rule-pack.json
  diff-from-previous.md
  agent-b-architecture-pack.md
  agent-c-implementation-notes.md
  validation-report.md
  loop-1-sdet-report.md
  loop-2-sdet-report.md
  loop-3-sdet-report.md
  agent-e-final-verification.md
```

세법 팩 run이 review 를 통과하면 위 실행 산출물 중 정본 대상은 아래 경로로 승격한다.

```text
plugins/year-end-harness/law-packs/2025/2025.04/
  source-manifest.json
  agent-a-tax-pack.md
  normalized-rule-pack.json
  diff-from-previous.md
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
- 현재 제품 기준 베이스라인을 아래 6개 축으로 나눠 점검한다.
  - `세법 팩`
  - `기본정보/부양가족/소득 입력`
  - `파싱`
  - `공제 항목 등록`
  - `부양가족 매칭`
  - `증빙자료/결과 계산`
- 실제 샘플 PDF를 기준으로 메인 사용자 흐름이 어디까지 동작하는지 점검한다.
- 계산 엔진은 별도로 truth audit를 수행한다.
- `ruleVersion`, `DeductionRule`, `CalculationResult`가 실제로 어느 수준까지 연결돼 있는지도 함께 기록한다.

### Phase 1. Agent A Tax Pack

- `tax-expert`와 `tax-law-pack` 스킬을 사용한다.
- `tax-pack-contract.md`와 `agent-a-tax-pack.md` 템플릿을 기준으로 작성한다.
- 공식 자료 수집, 가족 공제 요건 표, 항목별 가족 합산 규칙 표, 증빙 요구사항 표를 만든다.
- raw source snapshot 과 `source-manifest.json`을 남긴다.

게이트:

- 공식 출처 링크, 발행일, 효력일, 확인 시각이 있어야 한다.
- `confirmed`와 `inferred`가 분리돼 있어야 한다.

### Phase 1.5. Agent A Tax Pack Normalization

- Agent A가 `normalized-rule-pack.json`을 만든다.
- 현재 월 버전과 이전 월 버전의 `diff-from-previous.md`를 만든다.
- 각 규칙은 최소한 `ruleCode`, `parameters`, `effectiveFrom`, `effectiveTo`, `sourceRefs`, `confidence`를 가져야 한다.
- publish 가능 여부를 `READY_FOR_REVIEW` 또는 `BLOCKED`로 표시한다.
- review 를 통과한 월 버전만 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/` 정본 경로로 승격한다.

게이트:

- 월별 `ruleVersion` 결정 근거가 있어야 한다.
- 이전 버전 diff 또는 first version 표시가 있어야 한다.
- inferred 규칙은 명시적으로 표시돼야 한다.
- `source-manifest.json`, `normalized-rule-pack.json`, `diff-from-previous.md`가 모두 존재하고 계약 검증을 통과해야 한다.

### Phase 2. Agent B Architecture Pack

- `system-designer`와 `family-mapping-rules` 스킬을 사용한다.
- `architecture-pack-contract.md`, `family-mapping-contract.md`, `agent-b-architecture-pack.md` 템플릿을 기준으로 작성한다.
- DB 스키마 초안, 파싱 -> 공제 항목 등록 -> 가족 식별 -> 공제 청구 판정 -> 계산 -> 증빙 확인 흐름을 설계한다.
- 세법 팩 -> 정규화 룰팩 -> 사람 검토 -> Git 정본 승격 -> `PUBLISHED` 룰셋 게시 -> `RuleSetResolver` -> 계산 엔진 연결 흐름도 함께 설계한다.

게이트:

- 파싱 소유자와 공제 청구자 분리가 표현돼야 한다.
- 로직 트리가 항목별로 확장 가능해야 한다.
- `ruleVersion`, `ruleSetId`, `ruleSnapshotHash` 저장 전략이 표현돼야 한다.

### Phase 3. Agent C Implementation

- `fullstack-developer`가 허용 경계 안에서만 구현한다.
- `implementation-notes-contract.md`와 `agent-c-implementation-notes.md` 템플릿으로 구현 노트를 남긴다.
- 계산 엔진과 PDF import, deduction review 흐름을 연결한다.
- 세법 팩 관련 run에서는 가능한 한 아래처럼 작은 단위로 구현한다.
  - `ruleVersion 정리`
  - `RuleSetResolver`
  - `특정 공제 상수 제거`
  - `CalculationResult snapshot 보강`

### Phase 3.5. Repo Validation

- `repo-validation` 스킬을 사용한다.
- parser tests, backend regression, frontend build 중 필요한 조합을 선택한다.
- Windows 기준 검증 스크립트는 아래를 사용한다.
  - `plugins\year-end-harness\scripts\run-parser-tests.cmd`
  - `plugins\year-end-harness\scripts\run-backend-tests.cmd`
  - `plugins\year-end-harness\scripts\run-frontend-build.cmd`
- backend 검증은 같은 `build/` 디렉터리를 공유하므로 병렬 실행하지 않는다.
- 결과는 `validation-report-contract.md`와 `validation-report.md` 템플릿으로 남긴다.
- phase gate는 `plugins\year-end-harness\scripts\run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase validation`으로 확인한다.

### Phase 4. Agent D 3 Loops

- `sdet-loop`와 `verification-loop` 스킬을 사용한다.
- `docs/samples/scenarios/` fixture를 우선 사용한다.
- Loop 1: 기본 가족 시나리오와 단일 납세자
- Loop 2: 맞벌이 부부, 자녀, 노부모 포함
- Loop 3: 경계값과 증빙 누락, 동일 이름/동년생 충돌

각 루프는 `테스트 -> 결함 보고 -> Agent C 수정 -> 재검증`을 완료해야 한다.
- 세법 팩 관련 변경이면 월 버전 diff, 효력일 경계, snapshot 재현성을 함께 본다.

### Phase 5. Agent E Final Verification

- `qa-verifier`와 `verification-loop` 스킬을 사용한다.
- 다인가족 더미 PDF 또는 scenario fixture를 사용해 최종 납부/환급 계산과 증빙 판정을 검증한다.
- `final-verification-contract.md`와 `final-verification.md` 템플릿으로 결과를 남긴다.
- 최종 승인 전에는 `plugins\year-end-harness\scripts\run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase final`을 통과해야 한다.

## 8. 게이트와 중단 조건

- Agent A 산출물이 없으면 B와 C는 진행하지 않는다.
- Agent A의 정규화 룰팩이 `blocked`면 C는 세법 팩 연결 구현을 진행하지 않는다.
- Agent D의 3회 루프가 끝나지 않으면 E는 승인하지 않는다.
- `DONATION`, `CREDIT_CARD`처럼 미지원 정책이 남아 있으면 시뮬레이션 포함 여부를 명시적으로 차단하거나 경고해야 한다.
- 공식 소스가 충돌하거나 10% 이상 불확실성이 남으면 Agent A 단계로 되돌린다.
- validation report 없이 QA 단계로 넘어가지 않는다.
- 후행 phase 산출물이 선행 phase 산출물 없이 존재하면 `run-harness-gate`가 실패해야 한다.

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
- 세법 팩 없이 계산 코드에 숫자를 직접 하드코딩하는 것
- PDF 샘플 라인 없이 정규식을 바꾸는 것
- 가족 소유자 식별 확신이 부족한데도 자동 합산하는 것
- backend 검증을 병렬로 돌려 Gradle 산출물을 충돌시키는 것
- Agent D의 3회 루프를 생략하는 것
- Agent E가 미해결 결함을 안고 승인하는 것
- Markdown 세법 팩을 런타임 계산 입력으로 직접 쓰는 것
