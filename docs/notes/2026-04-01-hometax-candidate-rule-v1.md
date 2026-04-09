# 2026-04-01 추출 텍스트를 ParsedDeductionCandidate로 변환하는 1차 규칙 구현 기록

## 작업 개요

이번 작업은 4/1 첫 번째 작업에서 연결한 `실제 PDF 텍스트 추출 결과`를, 이제는 **조금 더 믿을 수 있는 공제 후보 1건**으로 바꾸는 단계였다.

첫 번째 작업 직후 상태는 아래와 같았다.

- PDFBox로 실제 PDF 텍스트를 읽을 수 있었다.
- `HometaxPdfImportParser`가 텍스트 레이어 여부를 판단할 수 있었다.
- 기존 샘플 데이터 대신 실제 PDF 기반으로 import 흐름이 연결되었다.

하지만 아직 큰 문제가 하나 있었다.

- 텍스트 안에 숫자가 보이면 너무 쉽게 금액 후보로 잡혔다.
- 그래서 홈택스 PDF의 `주민등록번호 뒷자리 1234567` 같은 숫자도 `보험료 금액`처럼 오인할 수 있었다.

즉, “텍스트를 읽는 단계”는 통과했지만 “읽은 텍스트를 의미 있는 공제 후보로 바꾸는 단계”는 아직 약했다.

이번 작업의 목표는 아래 3가지였다.

- 홈택스 PDF 텍스트 안에서 `의료비`, `보험료` 같은 섹션을 조금 더 정확하게 찾는다.
- 주민번호, 조회기간, 일련번호 같은 숫자는 금액 후보에서 제외한다.
- 여러 숫자 줄이 있더라도 `총합계`, `합계`처럼 의미가 큰 줄을 우선해서 `ParsedDeductionCandidate` 1건을 만든다.

## 왜 이 작업이 필요했는가

실제 고길동 홈택스 PDF 2페이지를 보면 아래처럼 숫자가 매우 많다.

```text
7: 고길동 750101-1234567
14: 01월 155,280 11,530 0 0
15: 02월 155,280 11,530 0 0
27: 합계 1,865,730 149,010 489,840 40,590
28: 총합계 2,545,170
```

이 PDF에서 우리가 정말 가져오고 싶은 값은 보통 `총합계 2,545,170` 쪽이다.

그런데 규칙이 단순하면 아래처럼 잘못 읽을 수 있다.

- `750101-1234567`에서 `1234567`을 금액처럼 판단
- 월별 행 `155,280`을 대표 금액처럼 판단
- 설명 문장 속 숫자를 금액으로 판단

신입 개발자 입장에서 이 문제를 이해할 때는 이렇게 보면 쉽다.

- 텍스트 추출은 “문서에서 글자 꺼내기”
- 후보 변환은 “꺼낸 글자 중 어떤 줄이 진짜 의미 있는 값인지 판단하기”

둘은 완전히 다른 문제다.

이번 작업은 두 번째 문제를 해결하는 첫 규칙 세트라고 보면 된다.

## 이번에 바뀐 파일과 역할

### 1. 실제 규칙이 들어간 파서 파일

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`

이 파일에서 바뀐 핵심은 아래와 같다.

- `첫 번째 매칭 줄`을 바로 반환하던 흐름을 버렸다.
- 페이지 전체를 돌면서 후보들을 수집한 뒤 `점수(score)`를 매긴다.
- 가장 점수가 높은 후보 1건만 `ParsedDeductionCandidate`로 반환한다.

즉 구조가 아래처럼 바뀐 것이다.

```text
이전
텍스트 한 줄 발견 -> 숫자 있으면 바로 후보 확정

지금
텍스트 한 줄씩 검사 -> 후보 목록 수집 -> 점수 계산 -> 가장 좋은 후보 1건 선택
```

### 2. 단위 테스트 파일

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`

이번에 추가/보강한 테스트 포인트는 아래와 같다.

- 텍스트 레이어 PDF에서 `의료비` 1건을 계속 잘 읽는지
- `보험료` 섹션에서 주민번호나 월별 행보다 `Grand total`을 우선하는지

### 3. 실제 파일 기반 검증 테스트

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`

이 테스트는 저장소 루트에 있는 실제 파일

- `고길동(750101)-2025년도자료.pdf`

를 그대로 읽어서 아래를 검증한다.

- `textLayerDetected = true`
- `DeductionType = INSURANCE`
- `amount = 2,545,170`
- `pageNumber = 2`
- `rawLineText`가 `총합계 2,545,170`을 포함하는지

이 테스트가 중요한 이유는 “우리가 만든 규칙이 장난감 PDF가 아니라 실제 홈택스 PDF에도 맞는가?”를 보여주기 때문이다.

## 코드 흐름 기준으로 보면 어떻게 동작하는가

### 1. PDF 전체 텍스트를 페이지별로 분리한다

위치는:

- `HometaxPdfImportParser.extractPages(...)`

여기서는 PDFBox의 `PDFTextStripper`를 사용해서 각 페이지를 문자열로 가져온다.

핵심 코드는 이런 흐름이다.

```java
stripper.setStartPage(pageNumber);
stripper.setEndPage(pageNumber);
String text = stripper.getText(document);
```

그 다음에는 줄 단위로 나눈다.

```java
text.lines()
    .map(this::normalizeWhitespace)
    .filter(StringUtils::hasText)
    .toList();
```

왜 줄 단위로 나누는가:

- 홈택스 PDF는 표처럼 보이지만 실제로는 “줄 문자열들의 묶음”으로 읽히는 경우가 많다.
- 그래서 파싱 시작점도 표 셀 단위가 아니라 줄 단위가 된다.

### 2. 섹션 제목을 먼저 감지한다

위치는:

- `HometaxPdfImportParser.detectRule(...)`

이 메서드는 한 줄 안에 아래 키워드가 있는지 본다.

- 의료비
- 보험료
- 교육비
- 기부금
- 영어 키워드도 일부 포함

예를 들면 `보험료` 쪽 규칙은 대략 이렇게 이해하면 된다.

```java
new CandidateRule(
    DeductionType.INSURANCE,
    "보험료",
    List.of("건강보험료", "보험료", "보험", "insurance", "premium")
)
```

여기서 중요한 포인트는 `DeductionType`과 키워드가 같이 묶여 있다는 점이다.

즉 한 줄에서 `건강보험료`를 찾으면,

- 저장 타입은 `INSURANCE`
- 세부 표시명은 `보험료`
- 이후 점수 계산도 보험 규칙 기준으로 진행

하게 된다.

### 3. 의미 없는 숫자 줄은 먼저 제외한다

위치는:

- `HometaxPdfImportParser.shouldSkipLine(...)`

이 메서드가 하는 일은 “숫자가 있어도 금액 후보로 보면 안 되는 줄”을 미리 걸러내는 것이다.

현재 제외하는 대표 패턴은 아래와 같다.

- 주민등록번호
- 조회기간
- 인적사항
- 일련번호
- 페이지 번호
- 설명 문장

예를 들어 이 줄은 이제 후보에서 제외된다.

```text
고길동 750101-1234567
```

왜냐하면 `\d{6}-\d{7}` 패턴은 주민번호 형식으로 간주하기 때문이다.

이 한 줄만으로도 이전 오탐의 큰 부분이 막힌다.

### 4. 남은 줄에서 금액을 뽑는다

위치는:

- `HometaxPdfImportParser.extractAmount(...)`

여기서는 아래 같은 숫자를 읽는다.

- `480,000`
- `2,545,170`
- `1865730`

패턴은 금액처럼 생긴 문자열만 뽑고,
그 뒤에 숫자가 아닌 문자가 붙어야 하도록 잡아 두었다.

또한 최소 금액도 조금 둔다.

```java
.filter(amount -> amount >= 10_000L)
```

왜 이렇게 하는가:

- 1, 2, 12 같은 작은 숫자는 금액보다 월 번호나 코드일 가능성이 크다.

### 5. 후보마다 점수를 매긴다

이번 작업의 핵심은 이 부분이다.

위치는:

- `HometaxPdfImportParser.scoreLine(...)`

대략 아래 기준으로 점수를 준다.

```text
+ 섹션 제목 안에 보험료/의료비 키워드가 있으면 가산점
+ 현재 줄 자체에 보험료/의료비 키워드가 있으면 가산점
+ "총합계"면 큰 가산점
+ "합계"면 가산점
+ "연말정산"이면 약간의 가산점
- 월별 행이면 감점
- 합계가 아닌데 숫자가 너무 많으면 감점
- 조회기간/내역 같은 설명성 문구는 감점
```

이 규칙 때문에 아래 줄들의 우선순위가 이렇게 달라진다.

```text
고길동 750101-1234567              -> 후보 제외
01월 155,280 11,530 0 0           -> 후보 가능하지만 점수 낮음
합계 1,865,730 149,010 ...        -> 후보 가능, 점수 높음
총합계 2,545,170                  -> 후보 가능, 점수 가장 높음
```

즉 이제는 “숫자가 먼저 나온 줄”이 아니라 “의미가 가장 큰 줄”을 가져오게 된다.

### 6. sourceName은 줄과 섹션 제목을 조합해서 만든다

위치는:

- `HometaxPdfImportParser.extractSourceName(...)`

예를 들어 `총합계 2,545,170`만 보면 기관명이나 세부 출처가 없다.

그래서 sourceName 생성은 두 단계로 한다.

1. 현재 줄에서 금액, 날짜, `총합계`, `합계` 같은 일반 단어를 지운다.
2. 그래도 의미 있는 값이 없으면 섹션 제목에서 sourceName을 만든다.

예를 들어 아래처럼 동작한다.

```text
현재 줄:      총합계 2,545,170
섹션 제목:    건강보험료(직장가입자) 내역 (단위:원)
sourceName:   직장가입자
```

이렇게 해 두면 summary 줄이어도 화면에 전혀 빈 값이 남지 않는다.

다만 실제 사용자 검증을 해보니,
`직장가입자`만 남으면 “이게 건강보험료인지 장기요양보험료인지” 문맥이 약했고,
컬럼 머리글에 있던 `① ② ③ ④` 같은 원형 숫자가 sourceName에 섞여 보일 수 있었다.

그래서 후속으로 sourceName 정리 규칙을 한 번 더 다듬었다.

- 컬럼 머리글에 있던 `①②③④` 제거
- `월별`, `고지금액`, `납부금액`처럼 표 머리글에 가까운 줄은 section title로 쓰지 않음
- summary 줄일 때는 section title에서 `건강보험료` 문맥을 살려 sourceName을 만듦

즉 지금 기대하는 결과는 아래 방향이다.

```text
이전: 1 장기요양 2 3 장기요양 4
지금: 건강보험료 직장가입자
```

이 조정은 “금액을 맞게 찾는 것” 이후에,
**사람이 화면에서 읽었을 때도 어떤 항목인지 자연스럽게 이해되게 만드는 단계**라고 보면 된다.

## 실제 예시로 보면 무엇이 달라졌는가

### 변경 전

실제 고길동 PDF에서 잘못 잡히던 예:

```text
고길동 750101-1234567
```

이 경우 뒷자리 `1234567`이 큰 숫자이기 때문에 금액처럼 보일 수 있었다.

결과 예:

```text
deductionType = INSURANCE
amount = 1234567
pageNumber = 2
```

이건 텍스트는 읽었지만 의미 해석이 틀린 상태다.

### 변경 후

이제는 아래 줄이 선택된다.

```text
총합계 2,545,170
```

결과 예:

```java
new ParsedDeductionCandidate(
    DeductionType.INSURANCE,
    "보험료",
    2_545_170L,
    null,
    "건강보험료 직장가입자",
    EvidenceStatus.SUBMITTED,
    ImportReviewDecision.needsReview(...),
    2,
    "건강보험료(직장가입자) 내역 (단위:원)",
    "총합계 2,545,170"
)
```

이 결과가 의미하는 바는 아래와 같다.

- 실제 2페이지 보험료 섹션을 읽었다.
- 월별 행이 아니라 총합계 줄을 골랐다.
- 여전히 자동 승인하지는 않고 `PENDING`으로 남긴다.

즉 정확도는 올리되, 업무 위험은 낮게 유지한 것이다.

## 어떤 코드를 먼저 읽으면 좋은가

신입 개발자 기준 추천 순서는 아래와 같다.

1. `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`
2. `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`
3. `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`
4. `backend/src/main/java/com/example/yearend/deduction/application/HometaxParsingDtos.java`
5. `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

왜 이 순서가 좋은가:

- 테스트를 먼저 보면 “무엇을 기대하는 코드인지”가 보인다.
- 그 다음 파서를 보면 “그 기대값을 어떻게 만들었는지”가 연결된다.
- 마지막에 DTO와 서비스까지 보면 import 전체 흐름이 닫힌다.

## 검증 결과

이번 작업 후 확인한 내용은 아래와 같다.

- `backend\\gradlew.bat test` 통과
- `HometaxPdfImportParserTest` 통과
- `HometaxPdfImportParserRealFileProbeTest` 통과
- 실제 `고길동(750101)-2025년도자료.pdf`에서 2페이지 보험료 총합계 `2,545,170` 선택 확인
- 실제 검증 과정에서 sourceName이 `건강보험료` 문맥으로 보이도록 후속 정리

즉 “텍스트 추출이 된다”를 넘어서,
이제는 “실제 홈택스 PDF에서 더 그럴듯한 공제 후보 1건을 뽑는다”까지 올라온 상태다.

## 이번 작업의 효과

이번 규칙 구현으로 얻은 효과는 아래와 같다.

- 주민번호 숫자 오탐을 크게 줄였다.
- 월별 금액보다 합계/총합계 줄을 우선하게 만들었다.
- 실제 홈택스 PDF 2페이지에서 보험료 총합계를 안정적으로 잡을 수 있게 됐다.
- 이후 `의료비`, `교육비`, `기부금`도 같은 방식으로 섹션별 정교화를 확장할 수 있는 구조가 생겼다.

## 아직 남아 있는 한계

아직 완성형 파서는 아니다.

- 여전히 현재 PoC 범위에서는 가장 점수가 높은 후보 1건만 가져온다.
- 표 구조를 완전히 이해하는 수준은 아니다.
- 부양가족 연결은 아직 하지 않는다.
- OCR은 없어서 텍스트 레이어가 없는 스캔 PDF는 처리하지 못한다.
- 보험료 안에서도 `총합계`가 항상 정답인지 문서 유형별 추가 검토가 필요하다.

그래도 이번 단계의 의미는 충분하다.

- 실제 PDF
- 실제 텍스트
- 실제 규칙
- 실제 테스트 파일

이 4개가 처음으로 한 덩어리로 연결되었기 때문이다.
