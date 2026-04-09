# 2026-04-01 홈택스 PDF 1건 기준 실제 텍스트 추출 PoC 연결 기록

## 작업 개요

이번 작업은 3단계 `간소화 자료 가져오기` 흐름에서 가장 비어 있던 부분인 **실제 PDF 텍스트 추출**을 처음으로 코드에 연결한 것이다.

3/31까지의 구현은 아래 흐름을 이미 갖고 있었다.

- 사용자가 `import-data` 화면에서 PDF를 업로드한다.
- 백엔드가 홈택스 import API를 받는다.
- 파싱 결과용 중간 DTO를 만든다.
- `DeductionItem`으로 저장하고 화면에 `자동 반영`, `확인 필요`로 보여준다.

하지만 그때까지는 `buildSampleParsedDocument(...)`가 하드코딩된 샘플 데이터를 만들어 주고 있었기 때문에,
업로드한 **실제 PDF 내용**이 import 결과에 전혀 반영되지 않았다.

이번 4/1 작업의 목표는 이 간극을 메우는 것이었다.

- 실제 업로드 PDF에서 텍스트를 읽는다.
- 텍스트 레이어가 있는지 확인한다.
- 현재 PoC 범위에서는 추출된 텍스트 중 **첫 번째로 매칭된 공제 후보 1건만** `ParsedDeductionCandidate`로 만든다.
- 그 결과를 기존 import 흐름에 그대로 연결한다.

즉 이번 작업은 “완전한 홈택스 파서 구현”이 아니라,
**샘플 데이터 기반 import 흐름을 실제 텍스트 추출 기반 흐름으로 교체하는 첫 연결 작업**이라고 보면 된다.

## 왜 이 작업이 필요했는가

기존 구조는 import UX와 review UX는 잘 만들어져 있었지만, 핵심 입력값인 PDF는 사실상 사용되지 않고 있었다.

이 상태의 한계는 분명했다.

- 사용자가 어떤 PDF를 올리든 결과가 항상 같았다.
- `textLayerDetected`, `rawLineText`, `pageNumber` 같은 메타데이터 필드가 실제 값이 아니라 샘플 값이었다.
- “실제 홈택스 PDF를 붙일 수 있는가?”라는 질문에 아직 코드로 답하지 못했다.

그래서 이번 작업에서는 범위를 욕심내지 않고 아래처럼 작게 끊었다.

- OCR은 하지 않는다.
- 표 전체를 다 읽지 않는다.
- 여러 공제 항목을 다 만들지 않는다.
- 대신 **텍스트 레이어 PDF 1건에서 공제 후보 1건을 실제로 추출하는 것**만 확실히 연결한다.

이렇게 작은 단위로 끊으면,
다음 단계에서 “2건”, “3건”, “섹션별 파싱”, “부양가족 매핑”을 붙일 때도 기반이 흔들리지 않는다.

## 이번에 바뀐 파일과 역할

### 1. 라이브러리 추가

파일:

- `backend/build.gradle`

추가 라이브러리:

- `org.apache.pdfbox:pdfbox:3.0.7`

왜 여기 넣었는가:

- 실제 PDF를 읽는 코드는 백엔드에서 실행된다.
- 따라서 의존성도 프론트가 아니라 `backend` 모듈의 `build.gradle`에 추가해야 한다.
- `implementation`으로 둔 이유는 테스트 코드뿐 아니라 실제 서비스 코드에서도 PDFBox를 사용하기 때문이다.

정리하면:

- 라이브러리 선언 위치: `backend/build.gradle`
- 실제 사용 위치: `backend/src/main/java/...`
- 테스트 사용 위치: `backend/src/test/java/...`

### 2. 실제 PDF 파서 추가

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`

이 파일을 이 폴더에 둔 이유:

- 이 코드는 단순 유틸이 아니라, “홈택스 PDF를 import 가능한 중간 DTO로 바꾸는 애플리케이션 규칙”이다.
- 그래서 `deduction/application` 계층에 두는 것이 자연스럽다.
- `infrastructure`에 두지 않은 이유는 DB, 외부 API, 파일 저장소 adapter라기보다 **유스케이스 중심 파싱 로직**에 가깝기 때문이다.

이 클래스의 책임은 크게 3가지다.

1. 업로드된 PDF에서 페이지별 텍스트를 뽑는다.
2. 텍스트 레이어가 있는지 판단한다.
3. 현재 PoC 규칙으로 공제 후보 1건을 찾아 `ParsedHometaxDocument`로 반환한다.

### 3. 기존 서비스와 파서 연결

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

기존에는:

- `buildSampleParsedDocument(...)`를 호출해서 샘플 데이터를 만들었다.

지금은:

- `hometaxPdfImportParser.parse(...)`를 호출해서 실제 PDF 기반 결과를 만든다.

즉 import 흐름의 나머지 단계는 거의 유지하고,
**중간 DTO를 만드는 부분만 실제 파서로 교체한 것**이 이번 변경의 핵심이다.

### 3-1. import-data 화면 간격도 후속으로 정리

파일:

- `frontend/app/import-data/page.js`

실제 업로드 검증을 하면서,
성공 메시지 배너와 `가져오기 허브` 카드가 너무 붙어 보여 읽기 흐름이 답답한 부분이 있었다.

그래서 성공 배너가 있을 때만 아래 여백이 생기도록 정리했다.

```jsx
{message ? (
  <div className="mb-6">
    <MessageBanner message={message} />
  </div>
) : null}
```

이건 기능 로직을 바꾼 것은 아니지만,
실제 사용자가 업로드 성공 직후 결과를 읽을 때 시선 흐름이 더 자연스럽게 이어지도록 만든 작은 UI 보정이다.

### 4. 실제 PDF 추출 테스트 추가

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`

왜 이 위치에 넣었는가:

- 테스트 대상이 `deduction/application/HometaxPdfImportParser.java`이기 때문이다.
- 보통 테스트는 프로덕션 코드와 같은 패키지 구조로 두는 편이 읽기 쉽고 찾기 쉽다.

이 테스트는 두 가지를 검증한다.

1. 텍스트 레이어가 있는 PDF에서 실제로 1건을 추출하는지
2. 텍스트가 없는 PDF에서는 후보를 만들지 않고 경고를 남기는지

## 코드 흐름 기준으로 보면 어떻게 동작하는가

아래 순서대로 따라가면 이번 작업의 실행 흐름이 잘 보인다.

### 1. 프론트가 PDF를 업로드한다

파일:

- `frontend/lib/yearEndApi.js`

`importHometaxPdf(sessionId, file)`가 `FormData`로 파일을 묶어서 아래 API를 호출한다.

- `POST /api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax`

즉 브라우저는 파일 바이너리를 그대로 백엔드에 넘긴다.

### 2. 컨트롤러가 MultipartFile을 받는다

파일:

- `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`

여기서는 `@RequestPart("file") MultipartFile file`로 PDF를 받는다.

이 단계의 핵심은 “파일 업로드를 받는다”이지, 파싱하지는 않는다.
실제 비즈니스 처리는 서비스로 넘긴다.

### 3. 서비스가 import 작업을 오케스트레이션한다

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

`importHometax()` 메서드 흐름은 지금 기준으로 아래와 같다.

1. 세션 소유권 확인
2. 빈 파일 업로드 방어
3. 파일명 결정
4. 이전 홈택스 import 항목 soft delete
5. `importBatchId` 생성
6. `importedAt` 생성
7. `hometaxPdfImportParser.parse(...)` 호출
8. `ParsedHometaxDocument.candidates()`를 `DeductionItem`으로 변환
9. 문서 체크리스트 동기화
10. 응답 DTO 반환

중요한 포인트는,
`DeductionItemService`가 PDF를 직접 읽는 것이 아니라 **파서에게 위임한다**는 점이다.

이렇게 분리하면 나중에 파서만 바꿔도 서비스 전체 흐름을 건드릴 필요가 적다.

### 4. 새 파서가 실제 PDF 텍스트를 추출한다

파일:

- `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`

이 클래스 안에서 실제로 일어나는 일은 아래와 같다.

#### 4-1. 파일 바이트를 읽는다

```java
byte[] pdfBytes = file.getBytes();
```

이 코드는 업로드된 `MultipartFile`을 메모리의 바이트 배열로 가져오는 단계다.

쉽게 말하면:

- 브라우저가 보낸 PDF 파일을
- 자바 코드가 다룰 수 있는 `byte[]` 형태로 바꾸는 작업이다.

#### 4-2. PDFBox로 문서를 연다

```java
try (PDDocument document = Loader.loadPDF(pdfBytes)) {
    ...
}
```

여기서 `PDDocument`는 “열린 PDF 문서 객체”라고 이해하면 된다.

비유하면:

- `MultipartFile`은 택배 상자
- `byte[]`는 상자 안 내용물
- `PDDocument`는 상자를 열어 실제 문서를 펼친 상태

#### 4-3. 페이지별 텍스트를 뽑는다

```java
PDFTextStripper stripper = new PDFTextStripper();
stripper.setStartPage(pageNumber);
stripper.setEndPage(pageNumber);
String text = stripper.getText(document);
```

이 단계에서 PDFBox가 각 페이지의 텍스트 레이어를 읽어 문자열로 반환한다.

여기서 중요한 점은:

- 지금은 OCR이 아니다.
- 즉 이미지 PDF에서 글자를 인식하는 것이 아니라,
- **PDF 안에 이미 존재하는 텍스트 레이어를 꺼내는 것**이다.

그래서 스캔본처럼 텍스트 레이어가 없는 PDF는 이 단계에서 거의 빈 문자열이 나온다.

#### 4-4. 줄 단위로 정리한다

추출된 문자열은 그대로 쓰기 어렵기 때문에,
공백을 정리하고 `lines()`로 나눠서 “한 줄씩 읽을 수 있는 상태”로 만든다.

이걸 하는 이유는 홈택스 표나 내역은 보통 “한 줄에 섹션명, 기관명, 금액, 날짜”가 섞여 있기 때문이다.

### 5. PoC 규칙으로 공제 후보 1건을 찾는다

현재는 아래 카테고리 키워드만 아주 단순하게 본다.

- 의료비
- 보험료
- 교육비
- 기부금

코드 안에서는 `CandidateRule` 목록으로 관리한다.

예를 들어 의료비 규칙은 대략 이런 의미다.

- `DeductionType.MEDICAL_EXPENSE`로 저장한다.
- 세부명은 `"의료비"`로 둔다.
- `의료비`, `medical expense`, `병원`, `약국` 같은 키워드를 보면 의료비 후보로 간주한다.

그다음 한 줄 또는 현재 줄 + 다음 줄 묶음에서 아래를 찾는다.

- 금액 패턴
- 날짜 패턴
- 출처 텍스트

### 6. 왜 “첫 번째 1건만” 가져오도록 했는가

이건 의도적인 제한이다.

홈택스 PDF 전체를 정확히 읽으려면 실제로는 아래가 필요하다.

- 표 구조 인식
- 머리글/합계 행 구분
- 페이지 넘김 처리
- 부양가족 연결
- 같은 항목 중복 제거
- 금액 합계 vs 상세 행 구분

이걸 한 번에 다 하려고 하면 PoC가 아니라 큰 기능 개발이 된다.

그래서 이번에는 아래만 확실히 만들었다.

- 실제 PDF 텍스트는 읽힌다.
- 텍스트에서 금액 포함 줄을 찾는다.
- 규칙에 맞는 1건을 기존 import DTO 구조로 바꾼다.

즉 이번 작업은 **진짜 데이터가 import 파이프라인 안으로 들어오기 시작했다**는 데 의미가 있다.

### 7. 찾은 결과를 `ParsedDeductionCandidate`로 바꾼다

예를 들어 추출된 줄이 아래와 같다고 생각해 보자.

```text
Medical expense Seoul General Hospital 2025-01-15 480,000
```

이 줄에서 파서는 대략 아래처럼 해석한다.

- 섹션 키워드: `Medical expense`
- 공제 유형: `MEDICAL_EXPENSE`
- 날짜: `2025-01-15`
- 금액: `480000`
- 출처: `Seoul General Hospital`

그리고 이것을 아래 같은 DTO로 바꾼다.

```java
new ParsedDeductionCandidate(
    DeductionType.MEDICAL_EXPENSE,
    "의료비",
    480_000L,
    LocalDate.of(2025, 1, 15),
    "Seoul General Hospital",
    EvidenceStatus.SUBMITTED,
    ImportReviewDecision.needsReview(
        "MEDIUM",
        "실제 PDF 텍스트에서 추출한 1건입니다. 항목 분류와 대상자 연결은 한 번 더 확인해 주세요."
    ),
    1,
    "Medical expense",
    "Medical expense Seoul General Hospital 2025-01-15 480,000"
)
```

여기서 중요한 점은 `needsReview(...)`를 쓴 부분이다.

왜 자동 승인하지 않았는가:

- 실제 텍스트는 읽었지만
- 아직 표 구조를 정밀하게 이해한 것은 아니고
- 부양가족 연결도 하지 않았고
- 합계 행인지 상세 행인지도 완전히 판별하지 않기 때문이다.

즉 지금 단계에서는 “읽기는 읽었지만, 사람 확인 한 번은 받자”가 더 안전하다.

## 이번 작업에서 새로 이해하면 좋은 설계 포인트

### 1. 파서와 서비스는 역할이 다르다

초보 때는 자주 “서비스에서 그냥 다 하면 안 되나?”라는 생각이 들 수 있다.
하지만 이번 구조를 보면 분리 이유가 분명하다.

`DeductionItemService`의 역할:

- 세션 확인
- 기존 데이터 정리
- import 배치 생성
- 저장
- 체크리스트 동기화
- 응답 반환

`HometaxPdfImportParser`의 역할:

- PDF 읽기
- 텍스트 추출
- 파싱 규칙 적용
- 중간 DTO 생성

즉 서비스는 **업무 흐름 관리자**, 파서는 **입력 해석기**라고 이해하면 된다.

### 2. `ParsedHometaxDocument`는 여전히 중요하다

이번 작업을 하면서도 import 파이프라인 전체를 바꾸지 않은 이유는,
이미 3/31에 만든 `HometaxParsingDtos` 구조가 좋은 완충 계층 역할을 하기 때문이다.

이번에도 결과는 여전히 아래 단계로 흐른다.

- PDF 원본
- `ParsedHometaxDocument`
- `ParsedDeductionCandidate`
- `DeductionItem`

즉 파서를 붙였어도 구조가 흔들리지 않았다.
이건 3/31 설계가 잘 되어 있었다는 뜻이다.

### 3. 왜 `application` 폴더에 파서를 두었는가

폴더 위치는 신입일 때 자주 헷갈린다.

이번 기준으로 보면:

- `api`: HTTP 요청/응답 입구
- `application`: 유스케이스와 흐름 제어, 파싱 규칙
- `domain`: 공제 타입, 정책, 엔티티 핵심 개념
- `infrastructure`: 저장소, 외부 자원 adapter 성격

새 파서는 “PDF를 어떻게 읽어서 import 후보로 바꿀지”라는 유스케이스 로직이므로
`backend/src/main/java/com/example/yearend/deduction/application`에 두는 것이 맞다.

## 테스트는 어떻게 검증했는가

### 1. 텍스트 레이어 PDF 테스트

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`

이 테스트는 PDFBox로 직접 작은 PDF를 만든다.

즉 테스트 안에서:

1. 메모리 위에 PDF를 생성하고
2. 텍스트를 써 넣고
3. 그 PDF를 `MockMultipartFile`로 감싸고
4. 실제 파서에 넣어 본다

이 방식의 장점은:

- 외부 샘플 파일에 의존하지 않는다.
- 테스트가 재현 가능하다.
- “실제 PDF 텍스트 추출”을 정말 검증한다.

### 2. 빈 PDF 테스트

두 번째 테스트는 텍스트 없는 PDF를 만들어 넣는다.

이 테스트가 중요한 이유는,
현실에서 사용자가 스캔본 PDF를 올릴 수 있기 때문이다.

지금 PoC는 OCR이 없으므로 이 경우:

- `textLayerDetected = false`
- `candidates = []`
- 경고 메시지 추가

처럼 동작해야 한다.

즉 “안 되는 케이스를 안전하게 실패시키는가”도 같이 본 것이다.

## 라이브러리와 폴더를 한 번에 정리하면

### 백엔드 의존성

- 추가한 라이브러리: `org.apache.pdfbox:pdfbox:3.0.7`
- 선언한 파일: `backend/build.gradle`
- 이유: 실제 PDF 텍스트 추출을 서비스 코드와 테스트 코드에서 모두 사용하기 위해

### 백엔드 메인 코드

- 새 파일: `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`
- 역할: 업로드 PDF에서 텍스트를 뽑고 `ParsedHometaxDocument`를 만드는 파서

### 백엔드 기존 연결 지점

- 수정한 파일: `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`
- 역할: 샘플 DTO 생성 대신 실제 파서를 호출하도록 변경

### 백엔드 테스트 코드

- 새 파일: `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`
- 역할: 실제 PDF 생성 기반 추출 테스트

## 이번 작업의 한계와 남은 리스크

이번 PoC는 분명 진전이지만, 아직 아래 한계가 있다.

### 1. OCR은 없다

텍스트 레이어가 없는 스캔 PDF는 아직 읽지 못한다.

### 2. 첫 번째 1건만 가져온다

여러 공제 항목이 들어 있어도 현재는 가장 먼저 매칭된 1건만 import한다.

### 3. 규칙 기반 매핑이 단순하다

지금은 키워드, 날짜, 금액을 단순 규칙으로 읽는다.
표 머리글/합계/상세 행 구분은 아직 약하다.

### 4. review-needed 정책이 보수적이다

실제 텍스트를 읽어도 자동 승인하지 않고 `확인 필요`로 둔다.
이건 정확도보다 안전성을 우선한 선택이다.

## 다음 단계 추천

다음 확장은 아래 순서가 가장 안전하다.

1. 현재 1건 추출을 2~3건까지 늘린다.
2. 섹션 제목과 상세 행을 분리하는 규칙을 넣는다.
3. 합계 행 필터링을 넣는다.
4. 부양가족/대상자 연결 규칙을 넣는다.
5. 텍스트 레이어가 없는 PDF를 위해 OCR 경로를 분리한다.

## 이번 작업을 한 문장으로 요약하면

이번 4/1 작업은
기존의 “홈택스 PDF 업로드 후 샘플 데이터 생성” 흐름을
**“업로드한 실제 PDF의 텍스트를 읽고, 그중 첫 번째 공제 후보 1건을 기존 import 파이프라인에 연결하는 흐름”으로 바꾼 작업**이다.
