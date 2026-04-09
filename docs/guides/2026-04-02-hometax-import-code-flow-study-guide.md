# 홈택스 PDF 업로드/파싱 코드 흐름 스터디 가이드

작성일: 2026-04-02  
대상: 1년차 신입 개발자  
목표: PDF 업로드 순간부터 파싱, DB 저장, 화면 반영까지 프론트/백엔드 흐름을 코드 기준으로 아주 구체적으로 이해한다.

---

## 1. 이 문서가 답하려는 질문

이 문서는 아래 질문에 답하기 위해 만들었다.

- 사용자가 PDF를 올리면 프론트에서는 정확히 어떤 함수가 먼저 호출되는가?
- 브라우저가 파일을 어떻게 서버로 보내는가?
- 백엔드는 `MultipartFile`을 어떻게 받고, PDFBox는 그 파일을 어떻게 읽는가?
- 파싱 결과는 어떤 클래스에 담기고, 어떤 기준으로 후보가 선택되는가?
- 파싱 결과는 DB 어디에 어떤 형태로 저장되는가?
- 저장된 데이터는 프론트에서 어떻게 다시 읽혀서 화면에 나타나는가?
- 지금 구현은 어디까지 되어 있고, 아직 한계는 무엇인가?

이 문서를 읽고 나면 적어도 아래 정도는 설명할 수 있어야 한다.

- "`pdfbox`를 Gradle에 추가했다"는 말이 실제로 무슨 뜻인지
- "`MultipartFile`과 `FormData`가 어떻게 연결되는지"
- "`attributesJsonb` 안에 왜 메타데이터를 넣는지"
- "`AUTO_APPLIED`, `NEEDS_REVIEW`가 어디서 결정되는지"

---

## 2. 먼저 전체 그림부터 보기

가장 먼저 큰 흐름을 머리에 넣자.

```text
사용자 PDF 선택/드롭
-> frontend/app/import-data/page.js 의 handleImport(file)
-> frontend/lib/yearEndApi.js 의 importHometaxPdf(sessionId, file)
-> fetch("/api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax")
-> Next.js rewrite
-> Spring Controller: DeductionItemController.importHometax(...)
-> Service: DeductionItemService.importHometax(...)
-> Parser: HometaxPdfImportParser.parse(...)
-> PDFBox 로 텍스트 추출
-> 후보 공제 항목 선택
-> deduction_items 저장
-> document_checklists 동기화
-> 응답 반환
-> 프론트가 deduction-items / document-checklists 재조회
-> 화면에 자동 반영/확인 필요 카드 렌더링
```

한 문장으로 말하면:

`파일 업로드 -> 서버 파싱 -> DB 저장 -> 프론트 재조회 -> 화면 렌더링`

---

## 3. 코드 지도를 먼저 머리에 넣기

이번 흐름에서 제일 중요한 파일은 아래다.

### 프론트

- `frontend/app/import-data/page.js`
  - 업로드 화면
  - 파일 선택 / 드래그앤드롭 처리
  - 업로드 후 재조회
  - 화면 렌더링
- `frontend/lib/yearEndApi.js`
  - fetch 공통 함수
  - 인증 헤더 부착
  - `FormData` 전송
  - 세션 조회/생성
- `frontend/lib/deductionImport.js`
  - import된 항목을 프론트에서 어떻게 해석할지 정의
  - `AUTO_APPLIED`, `NEEDS_REVIEW`, `MANUAL` 구분

### 백엔드

- `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`
  - 업로드 HTTP 엔드포인트
- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`
  - 업로드 오케스트레이션
  - 기존 import 항목 정리
  - 파서 호출
  - DB 저장
  - 체크리스트 동기화
- `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`
  - PDFBox로 텍스트 추출
  - 파싱 후보 선택
  - 점수 계산
- `backend/src/main/java/com/example/yearend/deduction/application/HometaxParsingDtos.java`
  - 파싱 결과 DTO
- `backend/src/main/java/com/example/yearend/deduction/domain/DeductionItem.java`
  - 공제 항목 엔티티
- `backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`
  - 증빙 체크리스트 생성/삭제
- `backend/src/main/resources/application.yml`
  - DB 설정, JPA 설정
- `backend/build.gradle`
  - PDFBox 포함 라이브러리 의존성

---

## 4. "PDFBox를 세팅했다"는 말의 진짜 의미

신입 때 가장 헷갈리는 부분 중 하나가 이거다.

> 라이브러리를 추가했다고 해서 PDF가 자동으로 파싱되는 건 아니다.

정확히는 아래 3단계다.

### 4.1 Gradle에 의존성을 추가한다

`backend/build.gradle`

```gradle
implementation 'org.apache.pdfbox:pdfbox:3.0.7'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

여기서 각 라이브러리 역할은 다르다.

- `pdfbox`
  - PDF를 읽고 텍스트를 추출하는 도구
- `spring-boot-starter-web`
  - HTTP 요청 받기
  - `MultipartFile` 받기
- `spring-boot-starter-data-jpa`
  - 파싱 결과를 DB에 저장하기

즉, 라이브러리 하나가 모든 걸 해주는 게 아니다.

### 4.2 코드에서 PDFBox 클래스를 직접 사용한다

`HometaxPdfImportParser.java`에서 실제로 사용하는 클래스는 이런 것들이다.

- `org.apache.pdfbox.Loader`
- `org.apache.pdfbox.pdmodel.PDDocument`
- `org.apache.pdfbox.text.PDFTextStripper`

즉, "세팅"의 진짜 뜻은 아래와 가깝다.

1. Gradle이 라이브러리를 내려받게 만든다.
2. Java 코드에서 그 라이브러리 클래스를 import한다.
3. 내가 직접 `loadPDF`, `getText` 같은 메서드를 호출한다.

### 4.3 중요한 오해 하나

PDFBox는 OCR 엔진이 아니다.

즉,

- 텍스트 레이어가 있는 PDF는 잘 읽을 수 있다.
- 스캔 이미지 PDF는 잘 못 읽는다.

현재 코드도 이 사실을 그대로 반영하고 있다.

`HometaxPdfImportParser.parse()` 안에서 텍스트 레이어가 없으면 경고를 남긴다.

---

## 5. 업로드 전 단계: 현재 세션부터 확보한다

업로드 화면은 그냥 뜨는 게 아니다. 먼저 "현재 작업 중인 세금 세션"을 알아야 한다.

### 5.1 프론트 진입

파일: `frontend/app/import-data/page.js`

페이지가 열리면 `useEffect` 안에서 `loadSnapshot()`을 호출한다.

이 함수는 아래 일을 한다.

1. 로그인 토큰이 있는지 확인
2. 사용자 정보 조회
3. 현재 세션 조회 또는 생성
4. 공제 항목 목록 조회
5. 증빙 체크리스트 조회

### 5.2 세션이 없으면 자동 생성

파일: `frontend/lib/yearEndApi.js`

`initializeAuthenticatedContext()`  
-> `ensureCurrentSession()`

`ensureCurrentSession()`의 동작:

1. localStorage에 저장된 세션 ID가 있으면 그 세션을 다시 조회
2. 없으면 `/tax-sessions` 목록 조회
3. 목록도 없으면 새 세션 생성

즉, 업로드는 항상 어떤 `taxSession`에 귀속된다.

이걸 꼭 이해해야 한다.

> 이 프로젝트에서 공제 항목은 "사용자에게 바로" 저장되는 게 아니라  
> "특정 tax session 아래에" 저장된다.(누가 작업 중인지”에 더해서 “어느 연도, 어떤 상태의 연말정산 작업인지”까지 함께 묶는 단위)

---

## 6. 프론트에서 파일 업로드가 시작되는 순간

이제 사용자가 PDF를 실제로 올리는 순간을 보자.

파일: `frontend/app/import-data/page.js`

### 6.1 파일 선택 방식

숨겨진 input이 있다.

```jsx
<input
  accept=".pdf,application/pdf"
  className="hidden"
  onChange={(event) => {
    const file = event.target.files?.[0];
    if (file) {
      void handleImport(file);
    }
    event.target.value = "";
  }}
  ref={fileInputRef}
  type="file"
/>
```

여기서 핵심은:

- 브라우저가 사용자가 선택한 파일을 `File` 객체로 준다.
- 그 `File` 객체를 그대로 `handleImport(file)`로 넘긴다.

### 6.2 드래그앤드롭 방식

같은 페이지에서 `onDrop`도 처리한다.

```jsx
onDrop={(event) => {
  event.preventDefault();
  setDragActive(false);
  const file = event.dataTransfer.files?.[0];
  if (file) {
    void handleImport(file);
  }
}}
```

즉, 파일 선택이든 드래그앤드롭이든 결국 종착지는 같다.

```text
handleImport(file)
```

### 6.3 `handleImport(file)`가 하는 일

`handleImport`는 아래 순서로 동작한다.

1. 파일과 세션이 유효한지 검사
2. 로딩 상태로 바꿈
3. 업로드 상태 문구를 보여줌
4. `importHometaxPdf(session.id, file)` 호출
5. 성공하면 `loadSnapshot()`으로 목록 재조회
6. 재조회된 데이터를 기반으로 화면 갱신

이 함수는 업로드의 프론트 출발점이다.

---

## 7. 프론트는 파일을 어떻게 서버로 보내나

파일: `frontend/lib/yearEndApi.js`

### 7.1 `importHometaxPdf(sessionId, file)`

```js
export async function importHometaxPdf(sessionId, file) {
  const formData = new FormData();
  formData.append("file", file);

  return request(`/api/v1/tax-sessions/${sessionId}/deduction-items/imports/hometax`, {
    method: "POST",
    body: formData
  });
}
```

여기서 중요한 개념이 `FormData`다.

### 7.2 `FormData`란?

쉽게 말하면 브라우저가 파일 업로드용 HTTP body를 만들기 위한 전용 객체다.

예를 들어 아래처럼 생각하면 된다.

```text
이름: file
값: 실제 PDF 파일 바이너리
```

브라우저는 이것을 `multipart/form-data` 형식으로 전송한다.

### 7.3 왜 JSON이 아니고 FormData인가?

파일은 보통 JSON으로 보내지 않는다.

JSON은 이런 데이터에 적합하다.

- 문자열
- 숫자
- boolean
- 배열
- 객체

하지만 파일은 바이너리 데이터이기 때문에 업로드에서는 보통 `multipart/form-data`를 쓴다.

### 7.4 `request()` 공통 함수가 하는 일

`request()`는 아래 일을 한다.

1. body가 `FormData`인지 확인
2. JSON이면 `Content-Type: application/json` 설정
3. `FormData`면 `Content-Type`을 직접 설정하지 않음
4. JWT 토큰을 `Authorization` 헤더에 붙임
5. `fetch()` 실행
6. 백엔드 응답을 `{ success, data, error }` 구조로 해석

여기서 특히 중요한 포인트:

> `FormData`일 때는 `Content-Type`을 직접 세팅하지 않는다.

왜냐하면 브라우저가 `boundary`까지 포함해서 올바른 헤더를 자동으로 만들기 때문이다.

이걸 수동으로 잘못 넣으면 업로드가 깨질 수 있다.

---

## 8. Next.js와 백엔드 연결

파일: `frontend/next.config.mjs`

```js
async rewrites() {
  return [
    {
      source: "/api/:path*",
      destination: "http://127.0.0.1:8080/api/:path*"
    }
  ];
}
```

이 설정 덕분에 프론트는 그냥 `/api/...`로 호출할 수 있다.

실제로는 아래처럼 이어진다.

```text
브라우저 fetch("/api/v1/tax-sessions/...")
-> Next.js dev server
-> 127.0.0.1:8080 의 Spring Boot 백엔드
```

즉, 프론트 코드 입장에서는 같은 origin처럼 보이지만, 실제 처리는 백엔드가 한다.

---

## 9. 인증은 어디서 확인하나

파일:

- `frontend/lib/yearEndApi.js`
- `backend/src/main/java/com/example/yearend/security/SecurityConfig.java`
- `backend/src/main/java/com/example/yearend/security/JwtAuthenticationFilter.java`

### 9.1 프론트

프론트는 localStorage에서 access token을 읽어 `Authorization: Bearer ...` 헤더를 붙인다.

### 9.2 백엔드

`JwtAuthenticationFilter`가 요청 헤더를 읽는다.

흐름은 아래와 같다.

1. `Authorization` 헤더 읽기
2. `Bearer `로 시작하는지 확인
3. 토큰이 유효한지 검증
4. username(subject) 추출
5. `UserDetailsService`로 사용자 조회
6. `SecurityContext`에 인증 객체 저장

그 다음 컨트롤러에서는 `@AuthenticationPrincipal UserDetails userDetails`로 현재 사용자를 받을 수 있다.

이게 왜 중요하냐면, 업로드 API는 세션 소유권을 검사하기 때문이다.

즉,

- 아무나 아무 세션에 파일을 올릴 수 없고
- 로그인한 사용자 본인 세션에만 업로드할 수 있다

---

## 10. 백엔드 업로드 진입점

파일: `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`

핵심 메서드:

```java
@PostMapping("/imports/hometax")
public ApiResponse<DeductionItemDtos.HometaxImportResponse> importHometax(
    @AuthenticationPrincipal UserDetails userDetails,
    @PathVariable UUID sessionId,
    @RequestPart("file") MultipartFile file
) {
    return ApiResponse.success(deductionItemService.importHometax(userDetails.getUsername(), sessionId, file));
}
```

### 여기서 꼭 이해할 것

#### `@RequestPart("file")`

프론트의 `formData.append("file", file)`와 이름이 정확히 맞아야 한다.

즉,

- 프론트 key: `"file"`
- 백엔드 파라미터 이름: `"file"`

이 이름이 다르면 백엔드가 파일을 못 받는다.

#### `MultipartFile`

이건 "업로드된 파일을 백엔드에서 표현하는 객체"다.

여기서 헷갈리지 말아야 한다.

> `MultipartFile`은 "DB에 저장된 파일"이 아니다.  
> HTTP 요청 안에 들어온 파일을 메모리/임시 저장 영역에서 다루기 위한 객체다.

즉, `MultipartFile` 자체는 영속 저장이 아니다.

원본 파일을 보관하고 싶다면 별도로:

- 디스크에 저장하거나
- S3 같은 외부 스토리지에 저장하거나
- DB에 blob으로 저장하는 로직을 직접 짜야 한다

현재 프로젝트는 그렇게 하지 않는다.

현재 프로젝트는:

- 파일을 받아서
- 파싱하고
- 파싱 결과만 DB에 저장한다

---

## 11. 서비스가 업로드를 총지휘한다

파일: `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

핵심 메서드: `importHometax(...)`

이 메서드는 이번 기능의 "현장 감독" 같은 역할이다.

### 11.1 순서별로 읽기

#### 1) 세션 소유권 확인

```java
TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
```

이 줄은 "이 세션이 진짜 네 것 맞아?"를 확인하는 줄이다.

#### 2) 파일 유효성 검사

```java
if (file == null || file.isEmpty()) {
    throw new BusinessException(ErrorCode.INVALID_REQUEST);
}
```

빈 파일 업로드 방지다.

#### 3) 파일명 확보

```java
String fileName = StringUtils.hasText(file.getOriginalFilename())
    ? file.getOriginalFilename()
    : "hometax-import.pdf";
```

원본 파일명이 있으면 그걸 쓰고, 없으면 기본 이름을 쓴다.

#### 4) 이전 import 항목 정리

```java
clearImportedItems(sessionId);
```

이 메서드는 현재 세션의 기존 import 항목을 `deletedAt`으로 soft delete 처리한다.

중요:

- 수동 입력 항목 전체를 지우는 게 아니다
- import된 항목만 지운다

즉, 새 PDF를 다시 올리면 "이전 import 결과를 갈아끼우는" 방식이다.

#### 5) import batch 생성

```java
UUID importBatchId = UUID.randomUUID();
OffsetDateTime importedAt = OffsetDateTime.now();
```

왜 필요할까?

- 같은 업로드에서 만들어진 항목 묶음을 식별하기 위해
- 프론트에서 "가장 최근 업로드"를 묶어서 보여주기 위해

#### 6) 파서 호출

```java
ParsedHometaxDocument parsedDocument = hometaxPdfImportParser.parse(session, file, fileName, importedAt);
```

이 줄이 "진짜 PDF 파싱 시작"이다.

#### 7) 파싱 후보를 엔티티로 저장

```java
List<DeductionItem> createdItems = parsedDocument.candidates().stream()
    .map(candidate -> createImportedItem(session, importBatchId, parsedDocument, candidate))
    .toList();
```

즉, 파서가 만든 후보 DTO를 DB 엔티티 `DeductionItem`으로 바꾸는 단계다.

#### 8) 증빙 체크리스트 동기화

```java
synchronizeDocuments(session);
```

공제 항목이 바뀌면 필요한 서류 목록도 달라지기 때문이다.

#### 9) 프론트가 쓸 응답 DTO 반환

```java
return new DeductionItemDtos.HometaxImportResponse(...)
```

이 응답에는 이런 정보가 들어간다.

- `importBatchId`
- `fileName`
- `importedAt`
- `importedCount`
- `autoAppliedCount`
- `needsReviewCount`
- `items`

---

## 12. PDF 파싱의 중심: `HometaxPdfImportParser`

파일: `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`

이 클래스가 PDFBox를 실제로 사용하는 곳이다.

### 12.1 클래스 역할

이 클래스는 아래 일을 한다.

1. 업로드된 PDF 바이트 읽기
2. 페이지별 텍스트 추출
3. 줄 단위로 정리
4. 어떤 줄이 의료비/보험료/교육비/기부금 후보인지 탐색
5. 금액/날짜/이름 같은 정보를 뽑아냄
6. 점수를 매겨 가장 그럴듯한 후보를 선택
7. 결과를 `ParsedHometaxDocument`로 반환

---

## 13. `parse()` 메서드를 한 단계씩 이해하기

### 13.1 `MultipartFile`에서 바이트 읽기

```java
pdfBytes = file.getBytes();
```

이 줄은 업로드된 파일 내용을 `byte[]`로 읽는다.

여기서 중요한 감각:

- 프론트의 `File`
- HTTP 요청의 multipart body
- 백엔드의 `MultipartFile`
- 파서 안의 `byte[]`

이렇게 형태가 바뀌며 흘러간다.

### 13.2 페이지 추출

```java
List<ExtractedPageText> pages = extractPages(pdfBytes, warnings);
```

이제 PDF 전체 바이트를 페이지별 텍스트 리스트로 바꾼다.

### 13.3 텍스트 레이어 존재 여부 확인

```java
boolean textLayerDetected = pages.stream().anyMatch(page -> !page.lines().isEmpty());
```

의미:

- 페이지 안에서 텍스트 줄이 하나라도 나오면 텍스트 레이어가 있다고 본다
- 아무것도 안 나오면 이미지 스캔 PDF일 가능성이 높다

### 13.4 후보 선택

```java
List<ParsedDeductionCandidate> candidates = textLayerDetected
    ? selectTopCandidates(session, pages, warnings)
    : List.of();
```

지금 구현에서는 텍스트 추출이 성공했을 때만 후보 선택을 진행한다.

### 13.5 현재 구현 상태

파서 타입이 `PDFBOX_TEXT_LAYER_POC`다.

이 이름이 말해주는 게 많다.

- `PDFBOX`
  - PDFBox 기반 텍스트 추출
- `TEXT_LAYER`
  - OCR이 아니라 텍스트 레이어 기반
- `POC`
  - 아직 개념검증 단계 성격이 강함

즉, 완성형 파서라기보다 1차 구현이다.

---

## 14. `extractPages()`는 실제로 무엇을 하나

이 메서드가 PDFBox 핵심 사용처다.

### 14.1 PDF 열기

```java
try (PDDocument document = Loader.loadPDF(pdfBytes)) {
```

이 줄은 PDF 바이트 배열을 PDF 문서 객체로 여는 단계다.

쉽게 말해:

- 그냥 파일 덩어리였던 `byte[]`
- -> PDF 구조를 이해하는 객체 `PDDocument`

### 14.2 텍스트 추출기 생성

```java
PDFTextStripper stripper = new PDFTextStripper();
stripper.setSortByPosition(true);
stripper.setLineSeparator("\n");
```

각 줄의 의미:

- `new PDFTextStripper()`
  - PDF에서 텍스트를 읽어 문자열로 바꾸는 도구 생성
- `setSortByPosition(true)`
  - PDF 내부 좌표 순서를 최대한 따라 텍스트를 정렬
  - 표 형식에서 순서가 덜 꼬이게 도움
- `setLineSeparator("\n")`
  - 줄바꿈 기준을 명시

### 14.3 페이지별 반복

```java
for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
    int pageNumber = pageIndex + 1;
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);
    String text = normalizeWhitespace(stripper.getText(document));
    pages.add(new ExtractedPageText(pageNumber, text, splitLines(text)));
}
```

이건 "한 페이지씩 읽기"다.

결과적으로 각 페이지마다 아래 정보가 생긴다.

- `pageNumber`
- `text`
- `lines`

### 14.4 왜 줄 단위가 중요한가

현재 파서는 표/행 기반으로 후보를 찾는다.

예를 들어 이런 줄이 있을 수 있다.

```text
Grand total 2,545,170
```

또는

```text
Seoul General Hospital 2025-01-15 480,000
```

이런 줄 하나하나를 검사해서:

- 금액이 있는지
- 날짜가 있는지
- 키워드가 있는지
- 총합계 같은 요약 줄인지

판단한다.

---

## 15. 파서는 어떤 공제 후보를 찾으려 하나

`CANDIDATE_RULES`를 보면 현재 지원 범위를 알 수 있다.

- `MEDICAL_EXPENSE`
- `INSURANCE`
- `EDUCATION_EXPENSE`
- `DONATION`

각 항목마다 키워드가 들어 있다.

예:

- 의료비 관련 키워드
- 보험 관련 키워드
- 교육비 관련 키워드
- 기부금 관련 키워드

즉, 지금 파서는 "홈택스 PDF 전체를 완벽히 이해"하는 게 아니라,

> 미리 정한 키워드와 패턴을 바탕으로  
> 특정 공제 후보를 찾는 규칙 기반 파서

라고 보는 게 맞다.

---

## 16. `selectTopCandidates()`는 왜 한 건만 뽑을까

현재 코드의 아주 중요한 특징이다.

```java
ScoredCandidate topMatch = matches.stream()
    .max(...)
    .orElseThrow();

return List.of(topMatch.parsedCandidate());
```

즉, 후보를 여러 개 찾더라도 지금은 가장 점수가 높은 한 건만 반환한다.

이건 현재 구현 한계다.

그래서 경고에도 이런 뜻이 담겨 있다.

- 지금은 1차 PoC다
- 가장 가능성 높은 공제 후보 1건만 가져온다

즉, "홈택스 PDF 전체 공제 내역을 다 읽어오는 완성형 기능"은 아직 아니다.

---

## 17. 후보를 만드는 핵심: `buildCandidate()`

이 메서드가 실제로 "이 줄을 공제 항목으로 볼지 말지" 결정한다.

흐름은 이렇다.

### 17.1 버릴 줄인지 먼저 판단

```java
if (shouldSkipLine(line)) {
    return Optional.empty();
}
```

예를 들어 이런 줄은 버린다.

- 주민등록번호 줄
- 페이지 번호 줄
- 조회기간 같은 메타정보 줄

왜 버리나?

이런 줄은 숫자가 있어도 공제 금액 줄일 가능성이 낮기 때문이다.

### 17.2 금액이 있는지 확인

```java
Optional<Long> amount = extractAmount(line);
if (amount.isEmpty()) {
    return Optional.empty();
}
```

금액이 없으면 공제 후보일 확률이 매우 낮다.

### 17.3 점수 계산

```java
int score = scoreLine(line, sectionTitle, rule);
if (score < 40) {
    return Optional.empty();
}
```

파서는 지금 AI 모델이 아니라 규칙 기반 점수 계산을 한다.

즉,

- 키워드가 있으면 점수 추가
- 총합계면 큰 점수 추가
- 월별 라인이면 감점
- 숫자가 너무 많이 섞인 표 행이면 감점

### 17.4 날짜 추출

```java
LocalDate usedAt = extractDate(line).orElse(null);
```

줄 안에 `2025-01-15`, `2025/01/15`, `2025.01.15` 같은 형태가 있으면 날짜를 뽑는다.

### 17.5 sourceName 추출

```java
String sourceName = extractSourceName(sectionTitle, line, rule, amount.get(), usedAt);
```

이건 사람이 보기 쉬운 "출처 이름"을 만드는 과정이다.

예를 들어:

- 병원명
- 보험 섹션명
- 기관명 비슷한 텍스트

를 남기고,

- 금액
- 날짜
- total
- 합계

같은 단어는 제거하려고 시도한다.

### 17.6 현재는 대부분 `needsReview`

가장 중요한 줄:

```java
ImportReviewDecision.needsReview("MEDIUM", "...")
```

즉 현재 구현은 파서가 후보를 만들어도 바로 자동 반영하지 않는다.

일단 기본은:

- `importBucket = NEEDS_REVIEW`
- `reviewStatus = PENDING`
- `confidenceLevel = MEDIUM`

로 들어간다.

이 사실 때문에 프론트에서도 "확인 필요" 영역이 중요하다.

---

## 18. `scoreLine()`은 어떤 기준으로 점수를 주나

이 함수는 규칙 기반 스코어링 엔진이다.

대략 이런 감각으로 보면 된다.

### 점수를 올리는 요소

- 섹션 제목이 있다
- 섹션 제목에 공제 관련 키워드가 있다
- 줄 자체에 공제 관련 키워드가 있다
- `grand total`, `total`, `합계`, `총합계` 같은 요약 의미가 있다

### 점수를 깎는 요소

- 월별 행처럼 보인다
- 숫자가 너무 많다
- 단위/조회기간 같은 메타 정보다

쉽게 말해:

> 파서는 "병원명/보험명 + 금액" 또는 "총합계 + 금액" 같은 줄을 좋아한다.

반대로,

> 파서는 "주민번호/월별표/메타정보" 같은 줄을 싫어한다.

---

## 19. 테스트로 보는 파서 의도

파일:

- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`
- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`

### 19.1 `HometaxPdfImportParserTest`

이 테스트는 가짜 PDF를 코드로 직접 만들어서 파서를 검증한다.

장점:

- 빠르고 안정적이다
- 원하는 케이스를 정확하게 재현할 수 있다

예를 들어 보험 테스트에서는 이런 줄을 만든다.

```text
Insurance premium report
Name 750101-1234567
Insurance premium details
01 month 155,280 11,530 0 0
02 month 155,280 11,530 0 0
Total 1,865,730 149,010 489,840 40,590
Grand total 2,545,170
```

이 테스트는 "주민번호나 월별 금액보다 총합계를 우선 선택해야 한다"는 의도를 보여준다.

### 19.2 `RealFileProbeTest`

이 테스트는 실제 워크스페이스 PDF로 검증하는 보조 성격이다.

이 파일은 정식 회귀 테스트라기보다:

- 실제 홈택스 PDF에서 파서가 어떻게 동작하는지
- 가짜 PDF 테스트와 실제 PDF 결과가 얼마나 비슷한지

를 보는 프로브에 가깝다.

---

## 20. 파싱 결과는 어떤 객체에 담기나

파일: `backend/src/main/java/com/example/yearend/deduction/application/HometaxParsingDtos.java`

### 20.1 `ParsedHometaxDocument`

이 객체는 "파일 전체 파싱 결과"다.

포함 정보:

- 파일명
- 파싱 시각
- 파서 타입
- 텍스트 레이어 검출 여부
- 경고 목록
- 후보 목록

쉽게 말하면:

> PDF 한 장(또는 여러 페이지 전체)을 읽은 최종 리포트

### 20.2 `ParsedDeductionCandidate`

이 객체는 "공제 후보 한 건"이다.

포함 정보:

- `deductionType`
- `subType`
- `amount`
- `usedAt`
- `sourceName`
- `evidenceStatus`
- `reviewDecision`
- `pageNumber`
- `rawSectionTitle`
- `rawLineText`

이게 중요한 이유:

> 지금 시스템은 원본 PDF를 DB에 저장하지 않기 때문에  
> `rawLineText`, `rawSectionTitle`, `pageNumber`가 디버깅 단서 역할을 한다.

### 20.3 `ImportReviewDecision`

이 객체는 import 결과에 대한 "검토 상태"를 표현한다.

주요 값:

- `importBucket`
- `reviewStatus`
- `confidenceLevel`
- `reviewReason`

예:

- `AUTO_APPLIED`
- `NEEDS_REVIEW`
- `EXCLUDED`

---

## 21. DB 저장: `DeductionItem`으로 변환

파일: `backend/src/main/java/com/example/yearend/deduction/domain/DeductionItem.java`

파싱이 끝났다고 바로 화면에 뜨는 게 아니다.

먼저 DB에 저장된다.

### 21.1 엔티티 핵심 필드

- `id`
- `taxSession`
- `dependent`
- `deductionType`
- `subType`
- `amount`
- `usedAt`
- `sourceName`
- `evidenceStatus`
- `attributesJsonb`
- `deletedAt`

### 21.2 가장 중요한 필드: `attributesJsonb`

이 컬럼은 PostgreSQL의 `jsonb` 컬럼이다.

현재 import 관련 메타데이터 대부분은 여기에 들어간다.

왜 이렇게 했을까?

가능한 이유:

- import 기능이 빠르게 확장되는 중이라 메타데이터가 자주 바뀔 수 있다
- 컬럼을 너무 많이 추가하지 않고 유연하게 저장하고 싶다
- 프론트가 표시용 부가 정보를 많이 필요로 한다

### 21.3 예시 JSON

실제 저장 모양은 대략 이런 느낌이다.

```json
{
  "sourceType": "HOMETAX",
  "sourceLabel": "Hometax PDF",
  "entryChannel": "IMPORT_SYNC",
  "importBatchId": "2e0f9e5c-8a63-4f8b-bd7b-f2d6e089b487",
  "importFileName": "고길동(750101)-2025년도자료.pdf",
  "importedAt": "2026-04-02T10:30:00+09:00",
  "importBucket": "NEEDS_REVIEW",
  "reviewStatus": "PENDING",
  "confidenceLevel": "MEDIUM",
  "reviewReason": "실제 PDF 텍스트에서 추출한 1차 후보입니다. 금액과 공제 항목을 검토한 뒤 확인 여부를 결정해 주세요.",
  "parserType": "PDFBOX_TEXT_LAYER_POC",
  "textLayerDetected": true,
  "parsingWarnings": [
    "현재 4/1 PoC에서는 가장 가능성 높은 공제 후보 1건만 가져옵니다."
  ],
  "pageNumber": 2,
  "rawSectionTitle": "건강보험료",
  "rawLineText": "총합계 2,545,170"
}
```

이 JSON이 나중에 프론트에서 중요하게 쓰인다.

---

## 22. "원본 PDF는 어디 저장되나?"에 대한 정확한 답

현재 코드를 기준으로 보면:

> 원본 PDF 파일은 저장하지 않는다.

정확히는:

- 브라우저가 파일 업로드
- 백엔드가 `MultipartFile`로 받음
- 파서가 `byte[]`로 읽음
- 텍스트 추출/후보 생성
- 후보 결과만 DB 저장

즉,

- S3 저장 없음
- 로컬 파일 저장 없음
- BLOB 컬럼 저장 없음

이걸 헷갈리면 안 된다.

현재 시스템은 "문서 보관 시스템"이 아니라 "문서로부터 추출한 구조화 데이터 저장 시스템"에 더 가깝다.

---

## 23. 증빙 체크리스트는 왜 같이 바뀌나

파일: `backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`

공제 항목이 생기면 필요한 증빙 문서도 달라진다.

예:

- 의료비 공제 -> 의료비 영수증
- 보험료 공제 -> 보험료 납입 증명

그래서 import 후 `synchronizeDocuments(session)`를 호출한다.

### 23.1 동기화 로직

1. 지금 살아 있는 공제 항목 목록을 가져온다
2. 더 이상 필요 없는 체크리스트는 삭제한다
3. 각 공제 항목에 대해 필요한 `DocumentType`을 결정한다
4. 해당 체크리스트가 없으면 생성한다
5. 제출 여부(`submittedYn`)도 공제 항목의 `evidenceStatus`를 기준으로 채운다

즉, 문서 체크리스트는 별도 수동 입력이 아니라 공제 항목에서 파생된다.

---

## 24. 프론트는 DB 값을 어떻게 해석해서 화면에 보여주나

파일: `frontend/lib/deductionImport.js`

이 파일이 아주 중요하다.

백엔드가 저장한 `attributesJsonb`를 프론트가 여기서 해석한다.

### 24.1 `parseDeductionAttributes(item)`

`attributesJsonb`는 문자열이므로 `JSON.parse()`가 필요하다.

즉,

- DB에서는 문자열(JSONB)
- 프론트에서는 객체로 변환해서 사용

### 24.2 `getDeductionSource(item)`

`sourceType`이 `HOMETAX`면 "홈택스 PDF에서 가져온 항목"으로 본다.

### 24.3 `isImportedDeduction(item)`

수동 입력이 아니라 import된 항목인지 판별한다.

### 24.4 `getImportBucket(item)`

이 함수가 화면 분류에 직접 쓰인다.

결과:

- `AUTO_APPLIED`
- `NEEDS_REVIEW`
- `MANUAL`

### 24.5 `isDeductionIncludedInCalculation(item)`

프론트도 review status를 보고:

- `PENDING`
- `EXCLUDED`

상태면 계산 반영 대상에서 제외한다.

즉, 프론트와 백엔드가 같은 개념을 공유하고 있다.

---

## 25. 업로드 후 화면이 바뀌는 이유

파일: `frontend/app/import-data/page.js`

사용자가 종종 이렇게 생각한다.

> "업로드 성공 응답이 왔는데, 화면은 어떻게 바뀌지?"

정답은:

> 업로드 응답만으로 화면을 직접 조작하는 게 아니라  
> 업로드 후 목록을 다시 읽어와서 state를 통째로 갱신한다.

### 25.1 핵심 코드

```js
const result = await importHometaxPdf(session.id, file);
await loadSnapshot();
```

즉:

1. 업로드 API 호출
2. 성공
3. `loadSnapshot()` 다시 실행
4. 공제 항목 목록 / 체크리스트 목록을 다시 가져옴
5. state 업데이트
6. React가 다시 렌더링

### 25.2 목록 재조회

`loadSnapshot()` 안에서:

```js
const [deductionList, checklist] = await Promise.all([
  listDeductionItems(context.currentSession.id),
  listDocumentChecklists(context.currentSession.id)
]);
```

즉, 화면 데이터의 원천은 백엔드 DB다.

프론트는 "업로드 API 응답만 믿고 임시로 그리는 것"보다 "다시 조회한 실제 저장 결과"를 보여준다.

이 방식은 안정적이다.

---

## 26. 화면은 어떻게 나뉘어 보이나

업로드 후 화면은 크게 세 종류로 나뉜다.

### 26.1 `autoAppliedItems`

```js
const autoAppliedItems = useMemo(
  () => importedItems.filter((item) => getImportBucket(item) === "AUTO_APPLIED"),
  [importedItems]
);
```

### 26.2 `needsReviewItems`

```js
const needsReviewItems = useMemo(
  () => importedItems.filter((item) => getImportBucket(item) === "NEEDS_REVIEW"),
  [importedItems]
);
```

### 26.3 `manualItems`

```js
const manualItems = useMemo(
  () => deductionItems.filter((item) => !isImportedDeduction(item)),
  [deductionItems]
);
```

즉, 화면은 DB의 공제 항목을 읽은 뒤,

- 홈택스 import 항목인지
- review status가 어떤지

를 기준으로 다시 분류해서 보여준다.

---

## 27. "확인 필요" 항목을 사용자가 승인하면 무슨 일이 일어나나

이것도 공부할 가치가 있다.

파일: `frontend/app/import-data/page.js`

### 27.1 프론트 `handleApprove(item)`

이 함수는 해당 item의 기존 `attributesJsonb`를 읽고 아래 값을 바꾼다.

- `importBucket: "AUTO_APPLIED"`
- `reviewStatus: "APPROVED"`

그리고 `updateDeductionItem(...)` API를 호출한다.

즉, "승인"은 별도 전용 API가 아니라 기존 공제 항목 수정 API를 통해 처리된다.

### 27.2 백엔드 update 흐름

`DeductionItemService.update(...)`  
-> `apply(...)`

이 과정에서 `attributesJsonb` 전체가 저장된다.

그 다음 다시 `synchronizeDocuments(...)`가 호출된다.

즉, review 상태가 바뀌면 문서 체크리스트에도 영향이 갈 수 있다.

---

## 28. 세션의 3단계 확정은 어디 저장되나

파일:

- `frontend/app/import-data/page.js`
- `backend/src/main/java/com/example/yearend/taxsession/application/TaxSessionService.java`
- `backend/src/main/java/com/example/yearend/taxsession/domain/TaxSession.java`

업로드 화면에는 "3단계 확정"이 있다.

이건 `tax_sessions`의 `basicInfoJsonb` 안에 저장된다.

예를 들어 프론트는 이런 JSON을 저장한다.

```json
{
  "dependentsConfirmed": true,
  "incomeConfirmed": true,
  "deductionsConfirmed": true
}
```

즉,

- 업로드 결과 자체는 `deduction_items`
- 단계 확정 여부는 `tax_sessions.basicInfoJsonb`

에 들어간다.

둘은 역할이 다르다.

---

## 29. 이 기능을 신입 관점에서 어떻게 이해하면 좋을까

이 기능은 사실 여러 기술이 겹쳐 있는 작은 통합 기능이다.

### 관점 1. HTTP 업로드 기능

- 브라우저가 파일을 보냄
- 백엔드가 multipart로 받음

### 관점 2. 문서 파싱 기능

- PDFBox가 텍스트를 추출
- 파서가 규칙 기반으로 후보 선택

### 관점 3. 도메인 저장 기능

- 공제 항목 엔티티 저장
- 증빙 체크리스트 생성

### 관점 4. UI 상태 반영 기능

- 업로드 후 재조회
- 화면 섹션 분류
- 승인/제외 처리

즉, 이 기능은 "PDF 파싱 기능 하나"가 아니라 사실상:

> 프론트 + 인증 + 업로드 + 파싱 + DB + 화면 상태관리

를 모두 지나가는 작은 end-to-end 기능이다.

그래서 공부하기 아주 좋다.

---

## 30. 현재 구현의 한계와 주의점

이건 꼭 알고 공부해야 한다.

### 30.1 현재는 PoC 성격이 강하다

파서 타입 이름이 `PDFBOX_TEXT_LAYER_POC`다.

### 30.2 현재는 후보 1건만 뽑는다

전체 PDF를 다 구조화하는 완성형 파서는 아니다.

### 30.3 현재는 기본적으로 `needsReview`

즉, 실제 자동 반영 로직은 아직 제한적이다.

### 30.4 OCR이 없다

스캔 PDF는 제대로 읽기 어렵다.

### 30.5 문자열 인코딩/깨짐 이슈가 보인다

코드와 테스트 문자열 일부에 깨진 한글이 보인다.

이건 앞으로 공부할 때 별도 포인트다.

- 파일 인코딩 문제인지
- PDF 텍스트 추출 시 문자 매핑 문제인지
- 소스 파일 저장 인코딩 문제인지

구분해서 봐야 한다.

### 30.6 원본 파일 저장이 없다

디버깅이나 재처리 관점에서는 장단점이 있다.

장점:

- 구현이 단순하다
- 저장 비용이 적다

단점:

- 나중에 "그때 어떤 PDF였는지" 다시 확인하기 어렵다

---

## 31. 추천 공부 순서

처음부터 모든 파일을 한 번에 보면 머리가 아프다.

이 순서로 읽는 걸 추천한다.

### 1단계. 프론트 출발점

- `frontend/app/import-data/page.js`
  - `loadSnapshot`
  - `handleImport`
  - `handleApprove`

### 2단계. 프론트 API 호출 공통부

- `frontend/lib/yearEndApi.js`
  - `request`
  - `importHometaxPdf`
  - `listDeductionItems`
  - `listDocumentChecklists`
  - `ensureCurrentSession`

### 3단계. 백엔드 진입

- `DeductionItemController.importHometax`
- `DeductionItemService.importHometax`

### 4단계. 파서 본체

- `HometaxPdfImportParser.parse`
- `extractPages`
- `selectTopCandidates`
- `buildCandidate`
- `scoreLine`

### 5단계. 저장 구조

- `DeductionItem`
- `DocumentChecklist`
- `TaxSession`

### 6단계. 프론트 표시 로직

- `deductionImport.js`

---

## 32. 손으로 따라가 보는 실전 추적 예시

예를 들어 루트에 있는 파일:

- `고길동(750101)-2025년도자료.pdf`

를 업로드했다고 가정하자.

### Step 1

브라우저에서 파일 선택  
-> `File` 객체 생성

### Step 2

`handleImport(file)` 호출  
-> 로딩 상태 true

### Step 3

`importHometaxPdf(session.id, file)`  
-> `FormData.append("file", file)`

### Step 4

`fetch("/api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax")`

### Step 5

Next rewrite  
-> Spring Boot로 전달

### Step 6

`DeductionItemController.importHometax(...)`  
-> `MultipartFile file` 수신

### Step 7

`DeductionItemService.importHometax(...)`  
-> 세션 소유권 확인  
-> 기존 import 항목 soft delete  
-> `importBatchId` 생성  
-> 파서 호출

### Step 8

`HometaxPdfImportParser.parse(...)`  
-> `file.getBytes()`  
-> `Loader.loadPDF(pdfBytes)`  
-> `PDFTextStripper.getText(document)`  
-> 페이지별 줄 정리  
-> 키워드/금액/날짜/합계 점수 계산  
-> 가장 높은 후보 1건 선택

### Step 9

`createImportedItem(...)`  
-> `deduction_items` 테이블에 저장  
-> `attributesJsonb`에 import 메타데이터 저장

### Step 10

`synchronizeDocuments(...)`  
-> `document_checklists` 생성 또는 정리

### Step 11

응답 반환

```json
{
  "success": true,
  "data": {
    "importBatchId": "...",
    "fileName": "고길동(750101)-2025년도자료.pdf",
    "importedAt": "...",
    "importedCount": 1,
    "autoAppliedCount": 0,
    "needsReviewCount": 1,
    "items": [
      ...
    ]
  }
}
```

### Step 12

프론트 `await loadSnapshot()`  
-> `listDeductionItems` 재조회  
-> `listDocumentChecklists` 재조회

### Step 13

React state 갱신  
-> `needsReviewItems`에 들어감  
-> 화면 카드로 렌더링

---

## 33. 헷갈리기 쉬운 포인트 정리

### Q1. PDFBox를 추가하면 왜 갑자기 PDF가 읽히는가?

A. 자동으로 읽히는 게 아니다.  
라이브러리를 프로젝트에 포함시켰고, 우리가 `Loader.loadPDF()`와 `PDFTextStripper.getText()`를 직접 호출해서 읽는 것이다.

### Q2. `MultipartFile`은 DB에 저장된 파일인가?

A. 아니다. HTTP 요청 안에서 전달된 업로드 파일 표현 객체다.

### Q3. 이 프로젝트는 원본 PDF를 저장하나?

A. 아니다. 현재는 파싱 결과만 저장한다.

### Q4. 파싱 결과가 화면에 왜 바로 보이나?

A. 업로드 성공 후 프론트가 목록을 다시 조회해서 state를 갱신하기 때문이다.

### Q5. 왜 `attributesJsonb`에 이것저것 다 넣나?

A. import 관련 메타데이터가 유동적이고 프론트 표시용 정보가 많아서, 고정 컬럼만으로 관리하기보다 JSONB로 유연하게 저장한 것이다.

### Q6. 왜 "확인 필요"가 많은가?

A. 현재 파서 구현이 기본적으로 `needsReview`를 반환하기 때문이다.

---

## 34. 지금 당장 해보면 좋은 공부 방법

### 방법 1. 함수 호출 체인 적어보기

직접 노트에 아래를 써보자.

```text
handleImport
-> importHometaxPdf
-> request
-> controller.importHometax
-> service.importHometax
-> parser.parse
-> extractPages
-> selectTopCandidates
-> createImportedItem
-> synchronizeDocuments
-> loadSnapshot
```

함수 하나 읽을 때마다 "다음 함수는 누구?"를 적어보면 좋다.

### 방법 2. 객체 형태 바뀌는 순간 표시해보기

이 흐름을 적어보자.

```text
브라우저 File
-> FormData
-> HTTP multipart body
-> Spring MultipartFile
-> byte[]
-> PDDocument
-> text lines
-> ParsedDeductionCandidate
-> DeductionItem(entity)
-> JSON 응답
-> React state
-> 화면 카드
```

이걸 이해하면 "데이터가 형태를 바꾸며 흐른다"는 감각이 생긴다.

### 방법 3. 디버거/로그 포인트 추천

다음 위치에 브레이크포인트를 찍어보면 좋다.

- `frontend/app/import-data/page.js`의 `handleImport`
- `frontend/lib/yearEndApi.js`의 `request`
- `DeductionItemController.importHometax`
- `DeductionItemService.importHometax`
- `HometaxPdfImportParser.parse`
- `HometaxPdfImportParser.extractPages`
- `HometaxPdfImportParser.buildCandidate`
- `DeductionItemService.createImportedItem`

---

## 35. 마지막 요약

이 기능의 핵심은 아래 한 문장이다.

> 사용자가 업로드한 PDF를 백엔드가 PDFBox로 읽고,  
> 규칙 기반으로 공제 후보를 뽑아 DB에 저장한 뒤,  
> 프론트가 그 저장 결과를 다시 조회해서 화면에 보여준다.

이 기능을 정확히 이해하려면 "파서만" 보면 안 된다.

반드시 같이 봐야 하는 것은:

- 프론트 업로드 시작점
- fetch/FormData
- Spring Controller
- Service 오케스트레이션
- PDFBox 파서
- 엔티티/DB 저장
- 체크리스트 동기화
- 프론트 재조회와 렌더링

이 흐름을 한 번 완전히 이해하면, 이후에:

- OCR 붙이기
- 여러 공제 항목 추출하기
- 자동 반영 기준 고도화
- 원본 파일 저장
- 에러 메시지 개선

같은 작업도 훨씬 쉽게 이해할 수 있다.

---

## 36. 같이 보면 좋은 파일 목록

- `frontend/app/import-data/page.js`
- `frontend/lib/yearEndApi.js`
- `frontend/lib/deductionImport.js`
- `frontend/next.config.mjs`
- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/example/yearend/security/SecurityConfig.java`
- `backend/src/main/java/com/example/yearend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`
- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`
- `backend/src/main/java/com/example/yearend/deduction/application/HometaxPdfImportParser.java`
- `backend/src/main/java/com/example/yearend/deduction/application/HometaxParsingDtos.java`
- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemReviewPolicy.java`
- `backend/src/main/java/com/example/yearend/deduction/domain/DeductionItem.java`
- `backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`
- `backend/src/main/java/com/example/yearend/document/domain/DocumentChecklist.java`
- `backend/src/main/java/com/example/yearend/taxsession/application/TaxSessionService.java`
- `backend/src/main/java/com/example/yearend/taxsession/domain/TaxSession.java`
- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserTest.java`
- `backend/src/test/java/com/example/yearend/deduction/application/HometaxPdfImportParserRealFileProbeTest.java`

---

## 37. 다음 스터디 추천

이 문서를 다 읽은 다음에는 아래 순서가 좋다.

1. `HometaxPdfImportParser.java` 한 줄씩 읽기
2. `DeductionItemService.importHometax()` 한 줄씩 읽기
3. 업로드 화면에서 실제로 PDF를 올리고 네트워크 탭 보기
4. DB에서 `deduction_items`, `document_checklists`, `tax_sessions` 직접 조회해보기
5. 승인 버튼(`handleApprove`) 흐름까지 이어서 추적하기

이 정도까지 하면 이번 기능은 상당히 깊게 이해한 것이다.
