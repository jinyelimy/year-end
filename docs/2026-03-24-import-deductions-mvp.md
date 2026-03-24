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
