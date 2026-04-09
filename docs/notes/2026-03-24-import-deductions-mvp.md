# 2026-03-24 간소화자료 + 공제항목 1차 구현 기록

## 작업 개요

이번 작업에서는 웹 우선 설계를 기준으로 `간소화 자료 가져오기`와 `공제 항목 입력` 흐름이 실제 코드에서 이어지도록 1차 MVP를 구현했다.

처음 목표는 다음 두 가지였다.

- 사용자가 홈택스 간소화 자료를 가져오면 `import-data` 화면에서 바로 검토할 수 있게 만든다.
- 검토가 끝난 항목은 `deductions` 화면과 4단계 증빙 흐름까지 자연스럽게 연결한다.

이번 1차 구현은 **가져오기 UX와 검토 흐름 연결**에 집중했다.
아직 실제 PDF 내용을 정교하게 파싱하는 단계는 아니며, 업로드된 파일을 기준으로 홈택스 샘플 데이터를 생성하는 방식으로 화면과 API 구조를 먼저 완성했다.

## 이번에 해결한 문제

- 기존 3단계는 `가져오기`와 `공제 입력`이 사실상 따로 놀고 있었다.
- 홈택스에서 가져온 항목과 사용자가 직접 입력한 항목을 구분해서 보여주는 기준이 없었다.
- `확인 필요` 상태인 항목까지 총공제 계산과 증빙 체크리스트에 바로 들어가면 사용자가 혼란스러울 수 있었다.
- 3단계 확정 여부와 실제 검토 완료 상태가 충분히 연결되어 있지 않았다.

## 구현 범위 요약

### 1. import-data 화면 개편

`frontend/app/import-data/page.js`

- 화면 역할을 `가져오기 허브 + 자동 해석 결과 검토`로 재정의했다.
- 파일 선택, 드래그 앤 드롭 기반으로 홈택스 PDF를 가져올 수 있게 했다.
- 가져오기 이후 결과를 아래 3개 구역으로 나눠 보여주도록 구성했다.
- `자동 반영됨`
- `확인 필요`
- `추가로 챙길 수 있는 공제`
- 현재 증빙 체크리스트도 같은 화면에서 미리 볼 수 있게 연결했다.
- `확인 필요` 항목은 이 화면에서 바로 승인하거나 제외할 수 있게 했다.

### 2. deductions 화면 개편

`frontend/app/deductions/page.js`

- 공제 원장을 `전체 / 홈택스 가져옴 / 직접 입력 / 확인 필요` 필터로 볼 수 있게 정리했다.
- 각 항목에 출처, 신뢰도, 검토 상태를 표시하도록 바꿨다.
- 홈택스에서 가져온 항목을 사용자가 수정하면 단순 수기 입력이 아니라 `가져옴 후 수정` 흐름으로 이어지도록 메타데이터를 유지했다.
- `확인 필요` 항목이 남아 있으면 3단계 확정을 막아서, 검토가 끝나지 않은 상태로 다음 단계로 넘어가지 않게 했다.

### 3. 백엔드 홈택스 가져오기 API 추가

`backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`  
`backend/src/main/java/com/example/yearend/deduction/api/DeductionItemDtos.java`  
`backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

- `POST /api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax` 엔드포인트를 추가했다.
- 프론트에서 `multipart/form-data`로 PDF 파일을 보내면 백엔드가 이를 받아 처리한다.
- 이번 1차 버전에서는 실제 PDF 파싱 대신 홈택스 샘플 항목을 생성한다.
- 생성된 항목에는 아래 메타데이터를 `attributesJsonb`에 저장한다.
- `sourceType`
- `sourceLabel`
- `entryChannel`
- `importBatchId`
- `importFileName`
- `importedAt`
- `importBucket`
- `reviewStatus`
- `confidenceLevel`
- `reviewReason`

이 메타데이터를 두는 이유는 단순히 금액만 저장하면 화면에서 아래 질문에 답할 수 없기 때문이다.

- 이 항목이 홈택스에서 온 것인가?
- 자동 반영 가능한가, 아니면 검토가 필요한가?
- 왜 검토가 필요한가?
- 사용자가 수정한 후에도 원래 가져온 항목이라는 사실을 유지할 수 있는가?

### 4. 체크리스트와 계산 로직 정리

`backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`  
`frontend/lib/yearEndView.js`

- 문서 체크리스트는 **실제로 유효한 공제 항목만 기준으로** 다시 동기화되도록 바꿨다.
- 홈택스에서 가져온 항목 중 `PENDING` 또는 `EXCLUDED` 상태인 항목은 증빙 체크리스트 생성 대상에서 제외했다.
- 총공제 계산에서도 `확인 필요` 상태 항목은 바로 반영하지 않도록 처리했다.

이 변경으로 사용자는 아직 검토하지 않은 항목 때문에 환급액이나 증빙 목록이 왜곡되는 일을 줄일 수 있다.

### 5. 공통 프론트 유틸 추가

`frontend/lib/deductionImport.js`  
`frontend/lib/yearEndApi.js`

- 가져온 항목 메타데이터를 읽고 쓰는 공통 헬퍼를 추가했다.
- `FormData` 업로드를 공통 API 요청 함수에서 처리할 수 있게 했다.
- 홈택스 가져오기 전용 `importHometaxPdf` 함수를 추가했다.

## 화면 흐름 기준으로 보면 어떻게 바뀌었는가

### 사용자 흐름

1. 사용자가 `import-data` 화면에서 PDF를 선택한다.
2. 프론트가 홈택스 가져오기 API를 호출한다.
3. 백엔드는 홈택스 샘플 공제 항목을 만든 뒤 응답한다.
4. 프론트는 결과를 `자동 반영됨 / 확인 필요 / 추가 공제 힌트`로 나눠서 보여준다.
5. 사용자가 `확인 필요` 항목을 승인하거나 수정한다.
6. 승인된 항목만 총공제 계산과 증빙 체크리스트에 반영된다.
7. `deductions` 화면에서는 최종 공제 원장을 출처와 함께 정리할 수 있다.

### 중요한 정책

- 자동 반영 가능한 항목은 바로 보여주되, 출처와 이유를 함께 표시한다.
- 검토가 필요한 항목은 바로 계산에 넣지 않는다.
- 사용자가 수정해도 원래 홈택스에서 가져온 항목이라는 사실은 유지한다.
- 3단계 확정은 `공제 입력 완료`가 아니라 `검토가 끝난 공제 입력 완료`로 해석한다.

## 파일별 핵심 변경 사항

### 프론트엔드

- `frontend/app/import-data/page.js`
- 가져오기 허브 UI 추가
- 결과 요약 카드 추가
- 자동 반영/확인 필요/추가 힌트 섹션 추가
- 승인/제외 액션 추가

- `frontend/app/deductions/page.js`
- 출처/검토 상태 중심 공제 원장 UI로 변경
- 확인 필요 필터 추가
- 3단계 확정 조건 강화

- `frontend/lib/deductionImport.js`
- 홈택스 가져오기 메타데이터 파싱/생성 유틸 추가

- `frontend/lib/yearEndApi.js`
- FormData 요청 지원
- 홈택스 가져오기 API 함수 추가
- 진행률/단계 활성화 계산 보조 함수 보강

- `frontend/lib/yearEndView.js`
- 총공제 계산 시 검토 완료된 항목만 반영

- `frontend/app/page.js`
- 대시보드 단계 상태를 순차 의존 구조로 보정
- 3단계 확정이 풀리면 4단계와 5단계도 함께 잠기도록 정리

### 백엔드

- `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemController.java`
- 홈택스 가져오기 API 추가

- `backend/src/main/java/com/example/yearend/deduction/api/DeductionItemDtos.java`
- 가져오기 응답 DTO 추가

- `backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`
- 홈택스 샘플 가져오기 로직 추가
- 가져온 항목 메타데이터 저장
- 가져오기 전 이전 홈택스 항목 정리
- 생성/수정/삭제/가져오기 후 체크리스트 동기화

- `backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`
- 더 이상 유효하지 않은 공제 항목과 연결된 체크리스트 제거

## 검증 결과

### 빌드 및 테스트

- `frontend`: `npm.cmd run build` 통과
- `backend`: `.\gradlew.bat test` 통과

### 실행 확인

- 프론트 개발 서버 응답 확인
- `http://127.0.0.1:3000/import-data`
- 백엔드 API 문서 응답 확인
- `http://127.0.0.1:8080/v3/api-docs`

### 브라우저 기준 확인한 동작

- 3단계 확정을 풀면 4단계 완료도 같이 풀린다.
- 1단계 확정을 풀면 2~5단계가 모두 잠긴다.
- `확인 필요` 항목이 남아 있으면 3단계 확정이 막힌다.

## 3/30 추가 분석 메모

### 1. `파싱 결과용 중간 DTO 구조 초안`의 의미

3/30에 정리한 `파싱 결과용 중간 DTO 구조 초안`은 “PDF를 읽어서 바로 DB에 넣는 구조”가 아니라, **파싱 결과를 한 번 정리한 뒤 저장 구조로 넘기기 위한 중간 표준 형식**을 만든 작업으로 보는 것이 정확하다.

이 구조가 필요한 이유는 아래와 같다.

- PDF 원본은 화면과 DB가 바로 쓰기 어려운 비정형 데이터다.
- 공제 항목마다 `자동 반영 가능`, `확인 필요`, `제외` 같은 판단이 함께 붙어야 한다.
- 페이지 번호, 원문 줄, 파서 경고처럼 DB 핵심 컬럼은 아니지만 추적에 중요한 정보도 같이 보존해야 한다.
- 이후 실제 PDF 파서를 붙일 때도, 파서 구현과 저장 구조를 느슨하게 분리할 수 있다.

즉 중간 DTO는 “파싱 결과를 DB 친화적인 형태로 바로 저장하는 객체”가 아니라, **파싱 결과를 애플리케이션이 이해할 수 있는 공통 언어로 바꾸는 객체**다.

### 2. `HometaxParsingDtos` 구조를 코드 기준으로 보면

`backend/src/main/java/com/example/yearend/deduction/application/HometaxParsingDtos.java`

현재 중간 DTO 초안은 아래 3개 record로 구성되어 있다.

- `ParsedHometaxDocument`
- `ParsedDeductionCandidate`
- `ImportReviewDecision`

각각의 역할은 다음과 같다.

#### `ParsedHometaxDocument`

PDF 파일 전체를 대표하는 파싱 결과 묶음이다.

- `fileName`: 어떤 파일을 파싱했는지
- `parsedAt`: 언제 파싱했는지
- `parserType`: 어떤 파서/전략으로 만들었는지
- `textLayerDetected`: PDF에서 텍스트 레이어를 감지했는지
- `warnings`: 파싱 과정의 경고 목록
- `candidates`: 공제 후보 목록

쉽게 말하면 “파일 1개를 읽은 결과 전체”다.

#### `ParsedDeductionCandidate`

공제 후보 1건을 뜻한다.

- `deductionType`: 최종 공제 대분류
- `subType`: 세부 분류
- `amount`: 금액
- `usedAt`: 사용일
- `sourceName`: 병원명, 보험사명, 학교명 같은 출처
- `evidenceStatus`: 증빙 상태
- `reviewDecision`: 자동 반영 또는 확인 필요 판단
- `pageNumber`: PDF 몇 페이지에서 찾았는지
- `rawSectionTitle`: PDF 섹션 원문 제목
- `rawLineText`: 실제 추출한 원문 줄

쉽게 말하면 “저장 후보가 되는 공제 항목 한 줄”이다.

#### `ImportReviewDecision`

이 후보를 가져온 뒤 어떻게 취급해야 하는지 표현하는 객체다.

- `importBucket`: `AUTO_APPLIED`, `NEEDS_REVIEW`, `EXCLUDED`
- `reviewStatus`: `APPROVED`, `PENDING`, `EXCLUDED`
- `confidenceLevel`: `HIGH`, `MEDIUM`, `LOW`
- `reviewReason`: 왜 그렇게 판단했는지 설명

이 객체가 분리되어 있기 때문에, 단순 금액 정보만이 아니라 “이 항목은 왜 바로 반영되지 않았는가?”까지 함께 다룰 수 있다.

### 3. `HometaxParsingDtos`는 실제로 언제 쓰이는가

겉으로 보면 `HometaxParsingDtos`가 직접 등장하지 않는 것처럼 보이지만, 실제로는 `DeductionItemService`에서 중첩 record 타입들을 import해서 사용하고 있다.

`backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

- `ParsedHometaxDocument parsedDocument = buildSampleParsedDocument(...)`
- `new ParsedDeductionCandidate(...)`
- `ImportReviewDecision.autoApplied(...)`
- `ImportReviewDecision.needsReview(...)`

즉 `HometaxParsingDtos`는 인스턴스를 만드는 클래스가 아니라, **파싱 결과 DTO 타입들을 한 파일에 묶어 두는 컨테이너 역할**을 한다.

정리하면 아래와 같다.

- `HometaxParsingDtos` 자체를 `new` 해서 쓰는 것은 아니다.
- 그 안에 들어 있는 `ParsedHometaxDocument`, `ParsedDeductionCandidate`, `ImportReviewDecision`을 실제 서비스 로직에서 사용한다.
- 현재 홈택스 가져오기 흐름은 이 중간 DTO를 한 번 만든 뒤, 이를 `DeductionItem` 엔티티로 변환해 저장한다.

### 4. `importHometax()` 기준 실제 실행 흐름

`backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

홈택스 가져오기 메서드는 아래 순서로 동작한다.

1. `taxSessionService.getOwnedSession(email, sessionId)`로 세션 소유권을 확인한다.
2. `file == null || file.isEmpty()`로 잘못된 업로드를 막는다.
3. `fileName`을 정한다.
4. `clearImportedItems(sessionId)`로 이전 홈택스 import 항목을 soft delete 처리한다.
5. `UUID importBatchId = UUID.randomUUID()`로 이번 가져오기 작업의 고유 ID를 만든다.
6. `OffsetDateTime importedAt = OffsetDateTime.now()`로 가져오기 시각을 기록한다.
7. `buildSampleParsedDocument(session, fileName, importedAt)`로 중간 DTO를 만든다.
8. `parsedDocument.candidates()`를 순회하며 `createImportedItem(...)`으로 `DeductionItem` 엔티티를 생성하고 저장한다.
9. `synchronizeDocuments(session)`로 증빙 체크리스트를 다시 맞춘다.
10. 저장된 엔티티를 응답 DTO로 변환해 프론트에 반환한다.

즉 `importHometax()`의 핵심은 “PDF를 바로 저장한다”가 아니라, **중간 DTO를 기준으로 가져오기 작업 전체를 오케스트레이션한다**는 점이다.

### 5. 예시로 보면 중간 DTO가 어떻게 만들어지는가

현재 샘플 구현 기준으로 의료비 48만원 1건은 아래처럼 만들어진다.

```java
new ParsedDeductionCandidate(
    DeductionType.MEDICAL_EXPENSE,
    "Hospital bill",
    480_000L,
    LocalDate.of(session.getTaxYear(), 1, 15),
    "Seoul General Hospital",
    EvidenceStatus.SUBMITTED,
    ImportReviewDecision.autoApplied(
        "HIGH",
        "Matched to a well-known medical expense section."
    ),
    1,
    "Medical expense details",
    "Medical expense / Seoul General Hospital / 480000"
)
```

이 후보는 아래 의미를 동시에 가진다.

- 의료비 공제 후보다.
- 금액은 48만원이다.
- 서울종합병원 데이터다.
- 증빙 상태는 제출됨이다.
- 자동 반영 가능하다.
- 1페이지 의료비 섹션에서 추출했다.
- 원문 줄은 무엇이었는지 추적할 수 있다.

즉 중간 DTO는 “공제 금액만 가진 구조”가 아니라, **추출 결과 + 판단 결과 + 추적 정보**를 함께 가지는 구조다.

### 6. 중간 DTO가 DB에 적용되는 방식

현재 프로젝트는 중간 DTO를 별도 테이블에 저장하지 않는다.
대신 `ParsedDeductionCandidate`를 `DeductionItem` 엔티티로 변환하면서 아래 두 층으로 나눠 저장한다.

#### 고정 컬럼으로 저장하는 값

`backend/src/main/java/com/example/yearend/deduction/domain/DeductionItem.java`

- `deductionType`
- `subType`
- `amount`
- `usedAt`
- `sourceName`
- `evidenceStatus`
- `taxSession`
- `dependent`

이 값들은 실제 공제 원장의 핵심 데이터이기 때문에 테이블 컬럼으로 보관한다.

#### `attributesJsonb`에 저장하는 값

`backend/src/main/java/com/example/yearend/deduction/application/DeductionItemService.java`

- `sourceType`
- `sourceLabel`
- `entryChannel`
- `importBatchId`
- `importFileName`
- `importedAt`
- `importBucket`
- `reviewStatus`
- `confidenceLevel`
- `reviewReason`
- `parserType`
- `textLayerDetected`
- `parsingWarnings`
- `pageNumber`
- `rawSectionTitle`
- `rawLineText`

즉 DB 관점에서 보면 현재 구조는 아래처럼 이해하면 된다.

- 공제 원장 핵심 값은 컬럼으로 저장
- 파싱/가져오기/검토 메타데이터는 `jsonb`로 저장

이 설계의 장점은 MVP 단계에서 빠르게 확장할 수 있다는 점이다.
반면 장기적으로는 `import item`과 `deduction`을 분리하는 편이 더 안전할 수 있다.

### 7. `UUID importBatchId`를 쓰는 이유

`importBatchId`는 이번 가져오기 1회를 구분하는 작업 단위 식별자다.

예를 들어 PDF 1개를 업로드해서 아래 4건이 생성될 수 있다.

- 의료비 1건
- 보험료 1건
- 교육비 1건
- 기부금 1건

이 4건은 각각 다른 `DeductionItem` row로 저장되지만, 모두 같은 `importBatchId`를 가진다.
이렇게 해야 아래 질문에 답할 수 있다.

- 이 공제 4건이 같은 업로드에서 왔는가?
- 가장 최근 가져오기 묶음은 무엇인가?
- 프론트에서 같은 가져오기 결과끼리 묶어 보여줄 수 있는가?

`UUID`를 쓰는 이유는 아래와 같다.

- 충돌 가능성이 매우 낮다.
- DB에 저장하기 전에 애플리케이션에서 바로 생성할 수 있다.
- 동시에 여러 요청이 와도 안전하게 작업 단위를 구분할 수 있다.

즉 여기서 `UUID`는 단순 PK 생성 습관이 아니라, **한 번의 import 작업을 유일하게 식별하기 위한 ID**로 쓰인다.

### 8. 화면과 체크리스트에는 어떻게 연결되는가

프론트는 `attributesJsonb`를 읽어서 아래 정보를 해석한다.

`frontend/lib/deductionImport.js`

- 이 항목이 `HOMETAX`에서 온 것인지
- 자동 반영인지 확인 필요한지
- 신뢰도는 어떤지
- 검토 이유는 무엇인지
- 최근 가져오기 묶음은 무엇인지

그래서 화면에서는 아래처럼 나눠 보여줄 수 있다.

- `자동 반영됨`
- `확인 필요`
- `홈택스 가져옴`
- `직접 입력`

또한 문서 체크리스트 동기화에서는 홈택스 import 항목 중 `PENDING`, `EXCLUDED` 상태를 제외하는 정책을 적용했다.
즉 “아직 사람이 검토하지 않은 항목”은 증빙 체크리스트를 바로 만들지 않도록 한 것이다.

### 9. 현재 초안 단계에서 주의해서 봐야 할 점

이번 구조는 흐름 설계 관점에서는 잘 잡혀 있지만, 아직 완전히 닫힌 구현은 아니다.

특히 현재는 아래가 사실이다.

- `buildSampleParsedDocument(...)`는 실제 PDF를 읽는 파서가 아니라 샘플 파서를 대신한다.
- 프론트와 체크리스트 쪽은 `reviewStatus`를 기준으로 동작한다.
- 하지만 세액 계산 서비스는 현재 `DeductionItem` 전체를 가져와 계산하기 때문에, `reviewStatus` 제외 정책이 계산 엔진까지 완전히 일관되게 연결되었는지는 추가 점검이 필요하다.

즉 3/30 작업의 핵심 가치는 “완성된 PDF 파서”가 아니라, **실제 파서를 붙일 수 있는 중간 DTO와 저장 흐름의 뼈대를 먼저 세운 것**에 있다.

### 10. 이 작업을 한 문장으로 요약하면

`파싱 결과용 중간 DTO 구조 초안 작성`은 홈택스 PDF에서 읽은 거친 데이터를 바로 DB에 넣지 않고,
중간 DTO로 한 번 정리한 뒤 공제 원장 저장, 검토 상태 표시, 증빙 체크리스트 연결까지 이어지도록
**파서와 저장 구조 사이의 완충 계층을 설계한 작업**이라고 정리할 수 있다.

## 아직 남아 있는 범위

### 1. 실제 PDF 파싱

지금은 업로드된 파일을 받아도 실제 PDF 내용을 읽지는 않는다.
다음 단계에서는 아래 순서로 확장해야 한다.

- PDF 텍스트 추출
- 홈택스 양식 판별
- 항목별 금액/대상자 추출
- 공제 카테고리 매핑
- 신뢰도 계산

### 2. import item과 최종 deduction 분리

현재는 기존 deduction item 구조 안에 홈택스 메타데이터를 함께 저장했다.
1차 구현에는 빠르지만, 장기적으로는 아래처럼 분리하는 편이 더 안전하다.

- `import item`: 원본 가져오기 결과
- `deduction`: 최종 반영된 공제 원장

이렇게 분리하면 원본 보존, 재가져오기 비교, 사용자 수정 이력 관리가 쉬워진다.

### 3. 웹 우선 가져오기 UX 고도화

다음 확장 후보는 아래와 같다.

- 최근 다운로드 파일 추천
- 브라우저별 디렉터리 선택 UX
- 가져온 파일 이력 관리
- `왜 이 항목이 검토 필요인지` 설명 고도화

## 이번 구현의 의미

이번 작업은 “홈택스 PDF를 업로드할 수 있다”에서 끝나는 구현이 아니라, 아래 질문에 답할 수 있는 구조를 만든 데 의미가 있다.

- 무엇이 자동 반영되었는가?
- 무엇이 아직 사람 검토가 필요한가?
- 무엇이 계산에 포함되었고, 무엇이 아직 제외되었는가?
- 증빙 단계와 어떻게 연결되는가?

즉 1차 MVP 기준으로는 **간소화 자료 가져오기, 공제 검토, 증빙 연결**이 하나의 사용자 흐름으로 이어지기 시작했다는 점이 가장 큰 변화다.
