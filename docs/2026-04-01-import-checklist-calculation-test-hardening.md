# 2026-04-01 홈택스 import / 체크리스트 / 계산 제외 규칙 테스트 보강 기록

## 작업 개요

이번 작업은 4/1에 만든 import 기능과 reviewStatus 정책이,
실제로 계속 안전하게 유지되도록 **회귀 테스트(regression test)** 를 보강한 작업이다.

기능을 붙인 뒤 테스트가 약하면 어떤 문제가 생기냐면,

- 다음 수정에서 주민번호 오탐이 다시 살아날 수 있고
- pending imported item이 다시 계산에 들어갈 수 있고
- 체크리스트가 생기면 안 되는 카드사용액에 체크리스트가 생길 수 있다

즉 기능 구현만으로는 부족하고,
“이 동작이 앞으로도 계속 유지된다”는 안전장치가 필요하다.

이번 작업의 목표는 아래 4가지를 테스트로 고정하는 것이었다.

1. 실제 홈택스 PDF 2페이지 보험료 총합계를 올바르게 고르는가
2. imported reviewStatus 정책이 manual/approved/pending/excluded를 올바르게 나누는가
3. 계산 서비스가 eligible item만 사용하고 있는가
4. 체크리스트는 필요한 공제 항목에만 생성되는가

## 이번에 바뀐 테스트 파일과 역할

### 1. 파서 단위 테스트 보강

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`

추가한 핵심 케이스:

- 보험료 섹션에서 `Grand total 2,545,170`을 대표 금액으로 고르는지
- 주민번호와 월별 행보다 총합계가 우선되는지

이 테스트는 “규칙 엔진 자체”를 빠르게 검증하는 용도다.

### 2. 실제 파일 기반 파서 검증

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`

이 테스트는 실제 파일

- `고길동(750101)-2025년도자료.pdf`

를 그대로 사용한다.

기존에는 출력만 해보는 probe 성격이 강했지만,
이번에는 실제 assert를 넣어 회귀 테스트로 승격했다.

검증 포인트는 아래와 같다.

- 텍스트 레이어가 감지되는가
- 공제 후보가 1건 생성되는가
- 유형이 `INSURANCE`인가
- 금액이 `2,545,170`인가
- 페이지가 `2`인가
- 원문 줄이 `총합계 2,545,170`인가

### 3. review 정책 테스트 추가

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/DeductionItemReviewPolicyTest.java`

이 테스트는 정책만 떼어서 본다.

장점:

- 계산 서비스 전체를 띄우지 않아도 규칙 자체를 빠르게 확인할 수 있다
- 실패했을 때 원인이 정책인지 서비스 조합인지 구분하기 쉽다

### 4. 계산 서비스 테스트 추가

파일:

- `backend/src/test/java/com/example/yearend/calculation/application/SimulationServiceTest.java`

이 테스트는 `SimulationService`가 정말로

- `getCalculationEligibleEntities(...)`

를 사용하고 있는지 확인한다.

즉 “정책이 존재한다”가 아니라,
“실제 계산 서비스가 그 정책을 탄다”를 검증한다.

### 5. 체크리스트 서비스 테스트 추가

파일:

- `backend/src/test/java/com/example/yearend/document/application/DocumentChecklistServiceTest.java`

이 테스트는 아래 업무 규칙을 검증한다.

- 보험료 공제 항목은 체크리스트를 생성한다
- 카드사용액 공제 항목은 체크리스트를 생성하지 않는다

이 테스트가 필요한 이유는,
문서 체크리스트는 공제 유형마다 필요한 증빙이 다르기 때문이다.

## 코드 흐름 기준으로 보면 테스트가 무엇을 막아 주는가

### 1. 파서 테스트는 “숫자 오탐 회귀”를 막는다

실제 홈택스 PDF에는 아래 숫자들이 같이 등장한다.

```text
750101-1234567
155,280
1,865,730
2,545,170
```

규칙이 조금만 흐트러져도 아래 같은 버그가 다시 생길 수 있다.

- 주민번호 뒷자리 선택
- 월별 1달 금액 선택
- 합계 대신 더 작은 숫자 선택

`HometaxPdfImportParserTest`와 `HometaxPdfImportParserRealFileProbeTest`는 바로 이 회귀를 막는다.

### 2. 정책 테스트는 “reviewStatus 해석 회귀”를 막는다

예를 들어 아래 JSON의 의미를 시스템이 잘못 이해하면 큰 문제가 된다.

```json
{
  "sourceType": "HOMETAX",
  "reviewStatus": "PENDING"
}
```

이 항목은 현재 업무 규칙상 계산 제외 대상이다.

`DeductionItemReviewPolicyTest`는 아래를 고정한다.

- manual은 포함
- approved import는 포함
- pending import는 제외
- excluded import는 제외

즉 문자열 한 글자만 바뀌어도 테스트가 알려 준다.

### 3. 서비스 테스트는 “정책이 실제로 사용되는지”를 막아 준다

정책 클래스가 아무리 잘 만들어져 있어도,
서비스가 그 메서드를 호출하지 않으면 소용이 없다.

그래서 `SimulationServiceTest`는 아래를 검증한다.

```text
run() -> getCalculationEligibleEntities() 사용
getRejections() -> getCalculationEligibleEntities() 사용
documentChecklistService.synchronize() -> 같은 eligible 목록 사용
```

이 테스트가 없다면,
나중에 누군가 다시 `getEntities()`로 되돌려도 바로 눈치채기 어렵다.

### 4. 체크리스트 테스트는 “문서 타입 매핑 회귀”를 막는다

`DocumentChecklistService`는 공제 유형을 문서 유형으로 바꾼다.

예를 들면:

- `INSURANCE` -> `INSURANCE_STATEMENT`
- `MEDICAL_EXPENSE` -> `MEDICAL_RECEIPT`
- `CREDIT_CARD` -> 없음

여기서 카드사용액에 문서 체크리스트가 생기면 UX가 이상해진다.

사용자는 “카드사용액 영수증을 올려야 하나?”라고 오해할 수 있다.

그래서 `DocumentChecklistServiceTest`가 그 경계를 지킨다.

## 실제로 어떤 테스트를 넣었는가

### 1. 고길동 PDF 실제 파일 테스트

핵심 assert 예시는 아래와 같다.

```java
assertThat(candidate.deductionType()).isEqualTo(DeductionType.INSURANCE);
assertThat(candidate.amount()).isEqualTo(2_545_170L);
assertThat(candidate.pageNumber()).isEqualTo(2);
assertThat(candidate.rawLineText()).contains("총합계 2,545,170");
```

이 테스트가 주는 안심 포인트는 명확하다.

- “우리 로컬 샘플 PDF에서 잘 보였다”가 아니라
- “저장소에 있는 실제 홈택스 파일을 테스트가 직접 검증한다”

### 2. SimulationService 계산 대상 테스트

여기서는 `DeductionItemService.getCalculationEligibleEntities(...)`가 반환한 목록만
계산과 체크리스트에 쓰였는지 검증한다.

왜 이런 방식이 좋은가:

- 계산 정책 자체는 `DeductionItemReviewPolicyTest`에서 보고
- 서비스 연결은 `SimulationServiceTest`에서 보기 때문에
- 실패 시 원인 추적이 쉽다

### 3. DocumentChecklistService 체크리스트 생성 테스트

핵심 관찰 포인트는 아래다.

```java
assertThat(saved.getDocumentType()).isEqualTo(DocumentType.INSURANCE_STATEMENT);
assertThat(saved.isSubmittedYn()).isTrue();
assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
```

이 테스트는 “체크리스트가 생기느냐”뿐 아니라,
생긴 뒤의 기본 상태까지 확인한다.

## 어떤 파일을 먼저 읽으면 좋은가

추천 순서는 아래와 같다.

1. `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`
2. `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`
3. `backend/src/test/java/com/example/yearend/deduction/application/DeductionItemReviewPolicyTest.java`
4. `backend/src/test/java/com/example/yearend/calculation/application/SimulationServiceTest.java`
5. `backend/src/test/java/com/example/yearend/document/application/DocumentChecklistServiceTest.java`

왜 이 순서가 좋은가:

- 먼저 “실제 PDF 결과”를 보고
- 그다음 “순수 규칙 테스트”를 보고
- 마지막에 “서비스 연결 테스트”를 보면

전체 그림이 단계적으로 들어온다.

## 이번 작업에서 같이 정리한 것

디버깅용으로 잠깐 사용했던 페이지 덤프 테스트는 최종 회귀 테스트 세트에서 제거했다.

제거한 파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfPageDumpProbeTest.java`

왜 제거했는가:

- 디버깅 순간에는 유용했지만
- CI나 정식 테스트 세트에서는 “출력 보기용” 테스트보다
- 명확한 assert를 가진 테스트가 더 가치 있기 때문이다

즉 이번 테스트 보강은 “많이 찍어 보는 테스트”가 아니라
“정답을 고정하는 테스트” 중심으로 정리한 것이다.

## 검증 결과

확인한 항목은 아래와 같다.

- `backend\\gradlew.bat test` 통과
- 실제 고길동 PDF 기반 파서 테스트 통과
- SimulationService 테스트 통과
- DocumentChecklistService 테스트 통과
- DeductionItemReviewPolicy 테스트 통과
- `frontend\\npm.cmd run build` 통과

## 이번 작업의 효과

이번 테스트 보강으로 얻은 효과는 아래와 같다.

- 고길동 PDF 2페이지 보험료 총합계 파싱이 회귀 테스트로 고정되었다
- imported reviewStatus 정책이 깨지면 바로 테스트가 실패한다
- 계산/체크리스트/문서 타입 매핑 경계가 테스트로 드러난다
- 다음 개발자가 코드를 수정해도 무엇을 지켜야 하는지 테스트가 문서 역할을 해 준다

신입 개발자 관점에서 가장 중요한 포인트는 이것이다.

- 테스트는 “코드가 돌아간다”를 확인하는 용도만이 아니다
- 테스트는 “팀이 앞으로도 지키고 싶은 약속”을 코드로 적어 두는 장치다

이번 작업은 바로 그 약속들을 테스트 파일에 적어 둔 단계라고 보면 된다.
