# 2026-04-01 imported reviewStatus 기준을 백엔드 계산 로직까지 통일 기록

## 작업 개요

이번 작업은 홈택스 import로 들어온 공제 항목의 `reviewStatus`를,
이제는 **프론트 표시 규칙이 아니라 백엔드 계산 규칙까지 같은 기준으로 적용**하도록 맞춘 작업이다.

문제는 간단히 말하면 아래였다.

- 프론트에서는 `PENDING`, `EXCLUDED`인 imported item을 계산 대상에서 빼고 있었다.
- 체크리스트 쪽도 일부 흐름에서는 같은 기준으로 제외하고 있었다.
- 그런데 계산 핵심 서비스인 `SimulationService`는 예전까지 공제 항목 전체를 그대로 사용하고 있었다.

즉 화면에서는 “아직 검토 전이라 제외되어 보이는 항목”이,
백엔드 실제 세액 계산에서는 포함될 수 있는 불일치가 있었다.

이번 작업의 목표는 아래 한 줄로 요약된다.

- `imported reviewStatus` 기준을 한 군데 정책으로 모으고, 계산과 체크리스트 동기화도 그 정책을 따르게 만든다.

## 왜 이 작업이 필요했는가

예를 들어 홈택스 PDF에서 가져온 보험료 1건이 있다고 하자.

속성 JSON이 이렇게 저장되어 있다면:

```json
{
  "sourceType": "HOMETAX",
  "reviewStatus": "PENDING",
  "importBucket": "NEEDS_REVIEW"
}
```

이 항목의 의미는 보통 아래와 같다.

- 실제 PDF에서 읽어 오긴 했다.
- 하지만 아직 사람이 검토하지 않았다.
- 그래서 계산에 바로 넣기엔 위험하다.

그런데 백엔드 계산이 이 항목을 그냥 포함해 버리면 어떤 문제가 생길까?

- 예상 환급액이 실제보다 커질 수 있다.
- 사용자는 “확인 필요”라고 본 항목이 이미 계산에 들어간 줄 모를 수 있다.
- 체크리스트와 결과 화면이 서로 다른 진실을 말하게 된다.

이건 실무에서 꽤 위험한 상태다.

화면 한 군데만 맞는 것이 아니라,
**도메인 정책이 계산과 문서 동기화 전체에 일관되게 적용되어야 한다.**

## 이번에 바뀐 파일과 역할

### 1. 공통 정책 클래스 추가

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemReviewPolicy.java`

이 파일이 이번 작업의 중심이다.

왜 새 클래스를 만들었는가:

- `reviewStatus` 해석 규칙을 여러 서비스에 흩뿌리면 나중에 기준이 또 달라진다.
- 그래서 “imported item이 계산에 포함되는가?”를 전담하는 작은 정책 클래스로 분리했다.

이 클래스의 핵심 메서드는 아래 4개다.

- `isImported(DeductionItem item)`
- `isIncludedInCalculation(DeductionItem item)`
- `isIncludedInDocumentChecklist(DeductionItem item)`
- `readAttributes(DeductionItem item)`

이 구조 덕분에 이제는 아래 질문을 같은 클래스에서 답한다.

- 이 항목이 홈택스 import인가?
- 계산에 넣어도 되는가?
- 체크리스트를 만들어도 되는가?

### 2. 공제 항목 서비스 보강

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

여기서 추가된 핵심 메서드는 아래다.

```java
public List<DeductionItem> getCalculationEligibleEntities(String email, UUID sessionId)
```

이 메서드는 모든 공제 항목을 가져온 뒤,
`DeductionItemReviewPolicy.isIncludedInCalculation(...)`로 한 번 더 필터링한다.

즉 이제는 “조회용 전체 목록”과 “계산용 목록”이 분리된 것이다.

이 차이가 왜 중요할까?

- 사용자는 목록 화면에서 import된 모든 항목을 볼 수 있다.
- 하지만 계산 엔진은 검토 완료된 항목만 사용한다.

실무에서는 이런 분리가 매우 흔하다.

- `보여주는 데이터`
- `계산에 쓰는 데이터`

가 항상 같지는 않기 때문이다.

### 3. 계산 서비스 수정

파일:

- `backend/src/main/java/com/example/yearend/calculation/application/SimulationService.java`

이번 변경 전에는 `SimulationService`가 사실상 전체 공제 항목을 기준으로 계산할 수 있었다.

이번 변경 후 핵심은 아래 두 지점이다.

#### 3-1. `run(...)`

이제 계산 시작 시 아래 메서드를 사용한다.

```java
List<DeductionItem> deductionItems =
    deductionItemService.getCalculationEligibleEntities(email, sessionId);
```

즉 계산 엔진으로 들어가는 목록 자체가 이미 필터링된 상태다.

#### 3-2. `getRejections(...)`

거절 사유 조회도 이제 같은 목록을 사용한다.

이 부분이 중요한 이유는,
계산 결과와 거절 사유 화면이 서로 다른 기준을 쓰면 또 혼란이 생기기 때문이다.

## 코드 흐름 기준으로 보면 어떻게 동작하는가

### 1. import된 항목은 `attributesJsonb` 안에 review 정보가 있다

위치는:

- `DeductionItemService.buildImportedAttributes(...)`

예시:

```json
{
  "sourceType": "HOMETAX",
  "reviewStatus": "PENDING",
  "confidenceLevel": "MEDIUM",
  "reviewReason": "실제 PDF 텍스트에서 추출한 1차 후보입니다."
}
```

즉 review 상태는 테이블 컬럼이 아니라 `attributesJsonb` 안의 메타데이터로 저장된다.

### 2. 정책 클래스가 JSON을 읽는다

위치는:

- `DeductionItemReviewPolicy.readAttributes(...)`

여기서 `ObjectMapper`로 JSON 문자열을 `Map<String, Object>`로 읽는다.

쉽게 말하면:

- DB에는 문자열로 저장되어 있지만
- 정책 클래스는 그것을 읽어서
- 자바 코드에서 `sourceType`, `reviewStatus`를 꺼내는 것이다.

### 3. imported item인지 먼저 판단한다

위치는:

- `DeductionItemReviewPolicy.isImported(...)`

핵심 기준:

```java
"HOMETAX".equals(readAttributes(item).get("sourceType"))
```

즉 `sourceType = HOMETAX`면 import된 항목이라고 본다.

manual 입력 항목은 여기서 false가 된다.

### 4. calculation 포함 여부를 판단한다

위치는:

- `DeductionItemReviewPolicy.isIncludedInCalculation(...)`

규칙은 현재 매우 명확하다.

- manual 항목이면 포함
- imported 항목이더라도 `APPROVED`면 포함
- imported 항목이 `PENDING`, `EXCLUDED`면 제외

코드 의미를 풀어 쓰면 아래와 같다.

```text
직접 입력: 계산 포함
홈택스 + APPROVED: 계산 포함
홈택스 + PENDING: 계산 제외
홈택스 + EXCLUDED: 계산 제외
```

### 5. 체크리스트 포함 여부도 같은 기준을 쓴다

위치는:

- `DeductionItemReviewPolicy.isIncludedInDocumentChecklist(...)`

현재는 이 메서드도 계산 기준과 같은 규칙을 사용한다.

즉 업무 규칙을 이렇게 맞춘 셈이다.

- 계산에서 빼는 import 항목은
- 체크리스트 생성 대상에서도 뺀다

이렇게 해야 사용자가 “계산에는 빠졌는데 체크리스트는 왜 생겼지?” 같은 혼란을 덜 겪는다.

### 6. SimulationService가 이제 이 기준을 실제로 사용한다

실행 흐름을 순서대로 보면 아래와 같다.

1. `SimulationService.run(...)` 진입
2. `DeductionItemService.getCalculationEligibleEntities(...)` 호출
3. 정책 클래스가 eligible item만 남김
4. 그 목록만 `DeductionEngine.evaluate(...)`로 전달
5. 그 목록만 `documentChecklistService.synchronize(...)`로 전달
6. 결과 저장 및 상태 변경

즉 이번 작업으로 “정책 정의”만 한 것이 아니라,
실제 계산 경로까지 그 정책을 타게 만들었다.

## 예시로 보면 무엇이 달라졌는가

### 예시 1. pending imported item

```json
{
  "sourceType": "HOMETAX",
  "reviewStatus": "PENDING"
}
```

변경 전 위험:

- deductions 화면에서는 `확인 필요`
- 그런데 계산 엔진에는 들어갈 수 있음

변경 후:

- deductions 화면에서는 `확인 필요`
- 백엔드 계산에서도 제외
- document checklist 동기화 대상에서도 제외

### 예시 2. approved imported item

```json
{
  "sourceType": "HOMETAX",
  "reviewStatus": "APPROVED"
}
```

변경 후:

- 계산 포함
- 체크리스트 포함
- 결과/체크리스트/화면 상태가 서로 같은 방향으로 정렬

### 예시 3. manual 입력 item

```json
{}
```

또는 `sourceType = MANUAL`

변경 후:

- 기존처럼 계산 포함
- 정책 적용이 manual 입력 흐름을 깨지 않는다

## 어떤 코드를 먼저 읽으면 좋은가

추천 순서는 아래와 같다.

1. `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemReviewPolicy.java`
2. `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`
3. `backend/src/main/java/com/example/yearend/calculation/application/SimulationService.java`
4. `frontend/lib/deductionImport.js`

왜 프론트 파일도 같이 보라고 하는가:

- 이번 작업의 본질이 “프론트 규칙과 백엔드 규칙을 맞추는 것”이기 때문이다.
- 프론트 helper가 어떻게 `reviewStatus`를 해석하는지 같이 보면 전체 그림이 선명해진다.

## 검증을 위해 추가한 테스트

관련 테스트 파일:

- `backend/src/test/java/com/example/yearend/deduction/application/DeductionItemReviewPolicyTest.java`
- `backend/src/test/java/com/example/yearend/calculation/application/SimulationServiceTest.java`

이 테스트들이 확인하는 내용은 아래와 같다.

### DeductionItemReviewPolicyTest

- manual item은 계산/체크리스트 포함
- approved imported item은 계산/체크리스트 포함
- pending imported item은 계산/체크리스트 제외
- excluded imported item은 계산/체크리스트 제외

### SimulationServiceTest

- `run(...)`이 `getCalculationEligibleEntities(...)`를 사용하고 있는지
- 계산에 들어간 목록이 그대로 체크리스트 동기화에도 쓰이는지
- `getRejections(...)`도 같은 eligible 목록 기준인지

이 테스트 구조가 좋은 이유는,

- 정책 자체가 맞는지
- 그 정책이 실제 계산 서비스에서 사용되는지

를 분리해서 검증하기 때문이다.

## 이번 작업의 효과

이번 정리로 얻은 효과는 아래와 같다.

- `reviewStatus` 기준이 백엔드 계산과 체크리스트까지 통일되었다.
- imported pending/excluded 항목이 결과 계산을 오염시키지 않게 되었다.
- 프론트와 백엔드가 서로 다른 기준을 쓰던 위험이 줄었다.
- 이후 승인/반려 워크플로우를 확장해도 정책 진입점이 하나로 모였다.

## 아직 남아 있는 한계

현재 정책은 단순하고 명확하지만, 앞으로 더 확장될 수 있다.

- `LOW_CONFIDENCE`처럼 confidence 기반 예외 규칙은 아직 없다.
- deduction type별로 checklist 포함 기준이 다를 수 있는데 현재는 reviewStatus 위주다.
- reviewer 승인 이력, 수동 override 이력 같은 정책은 아직 없다.

그래도 이번 단계에서 가장 중요한 것은 달성했다.

- “계산에 들어가면 안 되는 항목이 실제 계산에 들어가지 않도록 막았다.”

실무에서 이 한 줄은 생각보다 큰 차이를 만든다.
