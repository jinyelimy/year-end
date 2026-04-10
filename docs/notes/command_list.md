[연말정산 하네스 명령 모음]

이 문서는 연말정산 하네스를 "공식 소스 수집 -> 월별 세법팩 -> 정규화 룰팩 -> review -> Git 정본 승격 -> PUBLISHED 게시 -> 계산 연결" 구조로 운영하기 위한 명령 템플릿 모음이다.
기존 A2A phase 흐름을 유지하고, 세법 팩 문서화 단계를 `Phase 1 / Phase 1.5`에 끼워 넣는 방식으로 사용한다.

현재 목표 범위는 회사 제출 시스템이 아니라 환급/징수 계산 모듈이다.

## 먼저 기억할 원칙

- 한 번의 명령은 하나의 슬라이스만 다룬다.
- 세법 조사와 구현을 한 프롬프트에 한꺼번에 섞지 않는다.
- 세법 숫자는 공식 소스로 재검증하고, `confirmed / inferred`를 분리한다.
- Markdown 세법팩과 계산용 정규화 룰팩을 함께 만든다.
- `normalized-rule-pack.json`은 review/publish 후보 문서다.
- 계산에는 사람 검토를 거쳐 `PUBLISHED` 상태가 된 룰셋만 연결한다.
- 자동 수집은 허용하지만 자동 publish 는 하지 않는다.
- 각 작업은 가능하면 `.local/harness/<date>/<run-id>/`를 명시한다.
- 세법팩 작업은 가능하면 같은 `run-id`에서 `source-manifest.json`, `agent-a-tax-pack.md`, `normalized-rule-pack.json`, `diff-from-previous.md`를 함께 남긴다.
- review 를 통과한 월별 세법팩은 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/` 정본 경로로 승격한다.

## 메인 운영 순서

1. 현재 제품 구현 갭 점검
2. 현재 세법팩/룰셋 상태 점검
3. 공식 소스 수집
4. 월별 세법팩 작성
5. 정규화 룰팩 생성
6. 이전 버전 diff 검토
7. 월별 세법팩 정본 승격
8. publish 구조 설계 또는 구현
9. 계산 엔진 연결
10. 회귀 검증
11. 최종 QA

## Phase 0. 갭 확인 명령

### (1-1) 현재 제품 구현 갭 점검

목적:
기본정보, 부양가족, 소득, PDF 업로드, 파싱, 공제 항목 등록, 부양가족 매칭, 증빙자료, 결과확인이 실제 코드에서 어디까지 연결되어 있는지 먼저 확인한다.

명령: ★
하네스 기준으로 현재 계산 모듈의 구현 갭을 점검해줘.
Phase 0만 수행하고 코드 수정은 하지 마.
기본정보 입력, 부양가족 입력, 소득 입력, 홈택스 간소화 PDF 업로드, PDF 파싱, 공제 항목 등록, 부양가족 매칭, 증빙자료, 결과확인 계산이 실제 코드에서 어디까지 구현됐는지 정리해줘.
특히 실제 샘플 PDF 기준으로 섹션 인식, 사람 식별, 금액 추출, 공제 후보 등록 상태를 함께 정리해줘.
각 공제 타입에 대해 `없음 / 타입만 있음 / 파싱만 있음 / 등록 가능 / 매칭 가능 / 증빙 연결됨 / 계산 연결됨 / UI 반영됨` 상태를 표로 정리해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (1-2) 현재 세법팩/룰셋 상태 점검

목적:
현재 저장소가 세법을 어디까지 하드코딩하고 있는지, `ruleVersion`과 `DeductionRule`이 실제로 어떻게 쓰이는지 점검한다.

명령:★
하네스 기준으로 현재 세법팩/룰셋 상태를 점검해줘.
Phase 0만 수행하고 코드 수정은 하지 마.
현재 코드에서 하드코딩된 세법 숫자, `ruleVersion` 사용 경로, `DeductionRule` 실제 연결 여부, `CalculationResult`의 재현 가능성 상태를 정리해줘.
현재 월별 세법팩 구조가 없는 부분, `normalized-rule-pack.json`이 아직 없는 부분, publish 파이프라인이 없는 부분도 함께 정리해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

## Phase 1. 세법팩 수집/작성 명령

### (2-1) 공식 소스 수집

목적:
해당 월의 공식 원문과 메타데이터를 먼저 수집한다.

명령:★
하네스 기준으로 이번 달 연말정산 세법 공식 소스를 수집해줘.
Phase 1까지만 수행하고 코드 수정은 하지 마.
국세청 연말정산 종합안내, 홈택스 관련 공식 문서, 국가법령정보센터 법령 본문/개정이력, 법령해석 API, 기획재정부 RSS/보도자료를 수집해서 `source-manifest.json`과 raw source snapshot을 정리해줘.
출처명, URL, 발행일, 효력일, 확인 시각을 남겨줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (2-2) 월별 세법팩 작성

목적:
공식 소스를 사람이 읽는 월별 세법팩으로 정리한다.

명령:★
방금 수집한 공식 소스를 기준으로 하네스 기준 월별 세법팩을 작성해줘.
Phase 1부터 1.5까지만 수행하고 코드 수정은 하지 마.
`agent-a-tax-pack.md`를 만들고 세율, 공제 한도, 인적공제 판단, 가족 합산 가능 여부, 증빙 요구사항을 `confirmed / inferred / open-questions`로 정리해줘.
이번 달 `ruleVersion`도 제안해줘.
산출물은 같은 `run-id` 아래에 남겨줘.

## Phase 1.5. 정규화/검토 명령

### (2-3) 정규화 룰팩 생성

목적:
세법팩의 숫자와 효력일을 계산 엔진이 읽을 JSON으로 정규화한다.

명령:★
방금 만든 세법팩을 기준으로 하네스 기준 `normalized-rule-pack.json`을 생성해줘.
Phase 1.5까지만 수행하고 코드 수정은 하지 마.
각 규칙마다 `deductionType`, `subType`, `ruleCode`, `ruleCategory`, `parameters`, `effectiveFrom`, `effectiveTo`, `sourceRefs`, `confidence`를 넣어줘.
추론값은 `confidence=inferred`로 표시해줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (2-4) 이전 버전 diff 생성

목적:
새 월 버전이 이전 월 버전과 무엇이 달라졌는지 검토 가능하게 만든다.

명령:★
하네스 기준으로 이번 달 세법팩과 직전 월 세법팩의 diff를 만들어줘.
Phase 1.5까지만 수행하고 코드 수정은 하지 마.
`diff-from-previous.md`에 추가/변경/삭제 규칙, 영향받는 공제 타입, 계산 영향도를 정리해줘.
이전 버전이 없으면 first version이라고 명시해줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (2-5) publish 적합성 검토

목적:
정규화 룰팩이 계산에 연결될 준비가 되었는지 확인한다.

명령:★
하네스 기준으로 이번 달 `normalized-rule-pack.json`의 publish 적합성을 검토해줘.
Phase 1.5부터 2까지만 수행하고 코드 수정은 하지 마.
누락 출처, inferred 규칙, 효력일 충돌, 중복 ruleCode, 이전 버전 대비 위험 변경을 점검해줘.
publish 가능하면 `READY_FOR_REVIEW`, 아니면 `BLOCKED`로 판정해줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (2-6) 월별 세법팩 정본 승격

목적:
review 를 통과한 월별 세법팩을 Git 관리 정본 경로로 승격한다.

명령:★
하네스 기준으로 [run-id]의 월별 세법팩을 정본 경로로 승격할 준비 상태를 점검해줘.
Phase 1.5까지만 수행하고 코드 수정은 하지 마.
`.local/harness/<date>/<run-id>/` 아래의 `source-manifest.json`, `agent-a-tax-pack.md`, `normalized-rule-pack.json`, `diff-from-previous.md`를 검토 완료 기준으로 확인하고 `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/` 경로에 승격 가능한지 정리해줘.
이 단계에서는 아직 `PUBLISHED` 활성화는 하지 말고, 정본 승격 가능 여부와 누락 파일을 정리해줘.
산출물은 같은 `run-id` 아래에 남겨줘.

## Phase 2. 룰셋 연결 설계 명령

### (3-1) rule set publish 구조 설계

목적:
월별 세법팩을 DB와 계산 엔진에 연결하는 저장/해석 구조를 먼저 설계한다.

명령:★
하네스 기준으로 rule set publish 구조를 설계해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
Git law pack 정본, `normalized-rule-pack.json`, 사람 검토, `READY_FOR_REVIEW -> PUBLISHED` 상태 전이, `tax_rule_sets`, `deduction_rules`, `RuleSetResolver`, `ruleSnapshotHash`, rollback 전략을 포함해 설계해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (3-2) `ruleVersion` 정리 설계

목적:
현재 프론트/백엔드의 `ruleVersion` 형식을 일관되게 맞춘다.

명령:★
하네스 기준으로 현재 `ruleVersion` 정리 설계를 진행해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
현재 `2025.1`, `rule-2025.1`처럼 섞인 버전 문자열을 `YYYY.MM[.patch]` 규칙으로 통일하는 방안을 설계해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

## Phase 3. 룰셋 공통 구현 명령

이 섹션은 저장소 전체에 한 번 또는 소수 회 수행하는 공통 인프라 작업이다.
특정 공제 항목마다 반복 호출하는 기본 흐름은 아니다.

### (3-3) `RuleSetResolver` 구현

목적:
계산 시점에 `taxYear + ruleVersion + calculationDate` 기준으로 룰셋을 고정한다.

명령:★
방금 만든 설계를 기준으로 하네스 기준 `RuleSetResolver`를 구현해줘.
Phase 3부터 3.5까지만 수행해줘.
`DeductionRule` 또는 새 룰셋 저장 구조를 읽어 계산에 사용할 규칙 스냅샷을 결정하게 해줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (3-5) 계산 결과 스냅샷 보강

목적:
과거 계산을 다시 재현할 수 있게 만든다.

명령:★
하네스 기준으로 계산 결과 스냅샷 보강을 구현해줘.
Phase 3부터 3.5까지만 수행해줘.
`CalculationResult`에 `ruleVersion`뿐 아니라 `ruleSetId` 또는 `ruleSnapshotHash`를 저장하도록 보강해줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (3-6) PUBLISHED 룰셋 게시 구현

목적:
review 를 통과한 정본 세법팩만 런타임 활성 룰셋으로 게시한다.

명령:★
하네스 기준으로 `PUBLISHED` 룰셋 게시 구현을 진행해줘.
Phase 3부터 3.5까지만 수행해줘.
`READY_FOR_REVIEW` 상태의 정규화 룰팩이 사람 검토를 거친 뒤에만 `PUBLISHED`로 전이되고, 계산 엔진은 `PUBLISHED` 상태의 룰셋만 읽도록 구현해줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

## Phase 3. 공제 슬라이스 구현 명령

특정 공제 항목을 실제로 작업할 때의 기본 순서는 보통 `4-1 -> 4-2 -> 4-3`이다.
이미 구현된 정책 클래스를 룰셋 기반으로 바꾸는 경우에만 `3-4`를 추가로 사용한다.

### (4-1) 기존 공제 구현 상태 점검

명령:
하네스 기준으로 [공제명]의 현재 구현 상태를 점검해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
이미 구현된 부분과 누락된 부분을 `파싱/등록`, `매칭/증빙`, `계산/결과확인`, `세법팩/ruleCode 연결` 기준으로 나눠 정리해줘.
기존 구현은 최대한 재사용하고, 추가 설계가 필요한 부분만 제안해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (4-2) 특정 공제 설계

명령:
하네스 기준으로 [공제명] 설계를 진행해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
입력 소스, 공제 항목 등록, 부양가족 매칭, 증빙자료, 결과확인 계산, 그리고 월별 세법팩에서 어떤 ruleCode를 써야 하는지까지 함께 설계해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (4-3) 특정 공제 구현

명령:
방금 만든 설계를 기준으로 하네스 기준 [공제명] 구현을 진행해줘.
Phase 3부터 3.5까지만 수행해줘.
가능하면 `파싱/등록`, `매칭/증빙`, `계산/결과확인` 중 한 슬라이스만 선택해서 진행해줘.
필요한 parser tests / backend regression / frontend build를 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

### (3-4) 정책 클래스 상수 제거

목적:
이미 구현된 특정 공제 정책 클래스의 하드코딩 한도/공제율을 정규화 룰팩 기반으로 치환한다.

명령:
하네스 기준으로 [공제명] 정책 클래스의 하드코딩 숫자를 룰셋 기반으로 치환해줘.
Phase 3부터 3.5까지만 수행해줘.
보통 `4-1`과 `4-2`를 먼저 수행한 뒤, 이미 구현된 정책 클래스를 룰셋 기반으로 전환할 때 사용해줘.
정책 클래스가 상수 대신 `RuleSnapshot` 또는 동등한 읽기 모델에서 한도/공제율/효력일을 읽게 바꿔줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

## Phase 3.5. 저장소 검증 명령

### (3-7) 구현 후 저장소 검증

명령:
하네스 기준으로 이번 구현 범위의 저장소 검증을 수행해줘.
Phase 3.5만 수행해줘.
필요한 parser tests / backend regression / frontend build를 선택해서 실행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.

## Phase 4/5. 검증/QA 명령

### (5-1) 세법팩 회귀 검증

목적:
새 월 버전이 기존 계산을 의도치 않게 깨지 않았는지 검증한다.

명령:
하네스 기준으로 이번 달 세법팩 회귀 검증을 수행해줘.
Phase 4와 5를 수행해줘.
이전 월 버전 대비 diff가 계산 결과에 어떻게 반영되는지, 효력일 경계에서 오동작이 없는지, snapshot hash가 재현 가능한지 확인해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (5-2) 전체 계산 모듈 QA

명령:
하네스 기준으로 전체 계산 모듈 QA를 수행해줘.
Phase 4와 5를 수행해줘.
파싱, 공제 항목 등록, 가족 매칭, 증빙자료, 결과 계산, 세법팩 기반 한도/공제율 반영, API/화면 일관성을 함께 검증해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

## 자주 쓰는 조합 명령

### (6-1) 이번 달 세법팩 갱신 한 번에

하네스 기준으로 이번 달 연말정산 세법팩 갱신을 진행해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
공식 소스를 수집하고 `source-manifest.json`, `agent-a-tax-pack.md`, `normalized-rule-pack.json`, `diff-from-previous.md`를 만들어줘.
publish 적합성과 정본 승격 가능 여부까지 검토해줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (6-2) 특정 정책을 룰셋 기반으로 전환

하네스 기준으로 [공제명] 정책을 룰셋 기반으로 전환해줘.
Phase 0부터 3.5까지 수행해줘.
이 명령은 보통 `4-1 -> 4-2 -> 3-4`를 한 번에 묶어 실행하는 조합 명령으로 봐줘.
먼저 해당 공제의 월별 세법팩/정규화 룰을 점검하고, 그 다음 정책 클래스가 하드코딩 상수 대신 룰셋을 읽게 바꿔줘.
계산 엔진은 `PUBLISHED` 상태의 룰셋만 읽도록 유지해줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 `.local/harness/오늘날짜/<run-id>/`에 남겨줘.

### (6-3) 세법팩부터 QA까지 한 run으로

하네스 기준으로 [대상 범위]에 대한 세법팩 갱신부터 QA까지 진행해줘.
Phase 0부터 5까지 수행해줘.
공식 소스 수집, 월별 세법팩, 정규화 룰팩, publish 구조 검토, 구현, validation, 3회 loop, final verification까지 같은 run-id로 이어서 남겨줘.

## 실제 쉘 검증 명령

- 파서 테스트: `plugins\year-end-harness\scripts\run-parser-tests.cmd`
- 전체 백엔드 회귀: `plugins\year-end-harness\scripts\run-backend-tests.cmd`
- 프론트 빌드: `plugins\year-end-harness\scripts\run-frontend-build.cmd`
- 하네스 게이트: `plugins\year-end-harness\scripts\run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase <phase>`
