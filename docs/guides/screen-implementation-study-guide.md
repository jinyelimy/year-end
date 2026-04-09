# 화면 구현 구조 정리

이 문서는 `Easy-Tax` 프런트 화면이 어떤 구조로 동작하는지, 어떤 공통 코드와 API를 사용했는지 1년차 개발자 관점에서 이해하기 쉽게 정리한 문서다.

## 1. 먼저 큰 구조부터 보기

이 프로젝트의 프런트는 React 같은 프레임워크를 쓰지 않고, 아래 방식으로 구성되어 있다.

- 화면: `backend/src/main/resources/static/*.html`
- 공통 프런트 로직: `backend/src/main/resources/static/js/app.js`
- 백엔드 API: Spring Boot Controller
- 실제 업무 로직: Service
- 저장소 접근: Repository

즉 흐름은 아래와 같다.

```text
HTML 화면
  -> inline script에서 이벤트 처리
  -> window.YearEndApp 공통 함수 호출
  -> /api/v1/... fetch 요청
  -> Controller
  -> Service
  -> Repository / DB
```

이 구조의 장점은 단순하다는 점이다.
프레임워크가 없기 때문에, "버튼 클릭 -> JS 함수 실행 -> API 호출 -> 응답으로 DOM 갱신" 흐름을 눈으로 따라가기 좋다.

## 2. 공통으로 사용하는 핵심 코드

### 2-1. `app.js`가 하는 일

`backend/src/main/resources/static/js/app.js`는 화면마다 반복되는 공통 로직을 모아둔 파일이다.

주요 역할은 아래와 같다.

- JWT 토큰을 `localStorage`에 저장/삭제
- 로그인 사용자 정보 조회
- 현재 작업 중인 `tax session` 조회/생성
- 공통 `fetch` 요청 함수 제공
- 기본정보, 부양가족 관련 API 래핑
- 대시보드에서 다음 화면으로 어디로 갈지 계산
- 인증이 없으면 `auth.html`로 보내는 가드 역할

### 2-2. 공통 함수들을 왜 만들었는가

예를 들어 모든 화면에서 아래 로직이 반복될 수 있다.

- access token 꺼내기
- Authorization 헤더 넣기
- 응답 JSON 파싱하기
- 실패 시 에러 메시지 만들기
- 401이면 로그아웃 처리

이걸 각 화면마다 다시 쓰면 중복이 커진다.
그래서 `request()`, `initializeAuthenticatedContext()`, `requireAuthOrRedirect()` 같은 공통 함수를 `window.YearEndApp`에 묶어서 재사용하고 있다.

### 2-3. 인증/세션 공통 흐름

대부분의 보호된 화면은 아래 순서로 시작한다.

1. `requireAuthOrRedirect()`로 토큰 존재 여부 확인
2. `initializeAuthenticatedContext()` 호출
3. 내부에서 `getMe()` 호출
4. 내부에서 `ensureCurrentSession()` 호출
5. 세션이 없으면 `createTaxSession()`으로 새 세션 생성
6. 응답을 `localStorage`에 저장
7. 그 결과로 화면을 채움

이 패턴은 "로그인만 성공하면 대시보드/입력 화면에서 바로 작업할 수 있게" 만들기 위한 구조다.

## 3. 백엔드 구조를 같이 이해하기

### 3-1. Controller 역할

Controller는 URL을 받아서 Service로 넘겨주는 입구다.

예시:

- `AuthController`: 회원가입, 로그인
- `UserController`: 내 정보 조회
- `TaxSessionController`: 세션 생성, 조회, 기본정보 저장, 제출
- `DependentController`: 부양가족 CRUD
- `IncomeItemController`: 소득 항목 CRUD
- `DeductionItemController`: 공제 항목 CRUD
- `DocumentChecklistController`: 증빙 체크리스트 조회
- `SimulationController`: 계산 실행, 최신 결과 조회
- `AdminController`: 관리자 검토 기능

### 3-2. Service 역할

Service는 실제 업무 규칙을 담는 곳이다.

예를 들면:

- `AuthService`
  - 이메일 정규화
  - 중복 이메일 검사
  - 비밀번호 암호화
  - 로그인 인증
  - JWT 발급
- `TaxSessionService`
  - 세션 생성
  - 현재 유저 소유 세션인지 검사
  - 기본정보 저장
  - 제출 상태 변경
- `DependentService`
  - 세션 소유권 검증
  - 부양가족 생성/수정/삭제
  - soft delete 처리

### 3-3. 응답 형식 통일

백엔드는 `ApiResponse<T>`로 응답 형식을 통일했다.

```json
{
  "success": true,
  "data": { ... },
  "error": null,
  "timestamp": "..."
}
```

이 구조 덕분에 프런트 `request()` 함수에서 공통으로 처리할 수 있다.

### 3-4. 예외 처리 방식

`GlobalExceptionHandler`가 예외를 한 곳에서 정리한다.

- 비즈니스 에러 -> 코드와 메시지 반환
- 검증 실패 -> `fieldErrors` 반환
- 인증 실패 -> 401 응답
- 권한 부족 -> 403 응답

그래서 `auth.html`에서는 서버가 내려준 `fieldErrors`를 각 입력칸 아래에 바로 뿌릴 수 있다.

## 4. 화면별 구현 정리

## 4-1. `auth.html`

### 화면 목적

- 로그인
- 회원가입
- 로그인 성공 후 서비스 진입 준비

### 사용한 것

- Tailwind CSS
- 순수 JavaScript 이벤트 처리
- `window.YearEndApp.request()`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/signup`
- `GET /api/v1/users/me`
- `POST /api/v1/tax-sessions` 또는 기존 세션 조회

### 핵심 로직

1. 로그인/회원가입 탭 전환
2. 이메일 형식, 비밀번호 길이 등 클라이언트 1차 검증
3. 인증 API 호출
4. 성공하면 access/refresh token 저장
5. `initializeAuthenticatedContext()`로 사용자 정보와 현재 세션 준비
6. `index.html`로 이동

### 구현 포인트

- `callAuth()`에서 `auth: false` 옵션을 줘서 토큰 없이 호출한다.
- `setSubmitting()`으로 중복 제출을 막는다.
- 서버의 `fieldErrors`를 받아 `applyServerFieldErrors()`로 각 필드에 표시한다.
- 이미 토큰이 있으면 다시 로그인 화면에 머물지 않고 대시보드로 보낸다.

### 공부 포인트

- 클라이언트 검증과 서버 검증을 둘 다 사용하는 이유
- 로그인 후 바로 메인 화면으로 보내지 않고, 왜 사용자/세션 초기화까지 먼저 하는지

## 4-2. `index.html`

### 화면 목적

- 로그인 후 첫 진입 화면
- 현재 세션 상태를 요약해서 보여주는 대시보드
- 다음 작업 화면으로 이동 유도

### 사용한 것

- `requireAuthOrRedirect()`
- `initializeAuthenticatedContext()`
- `resolveProgress()`
- `resolveNextStep()`
- `getSessionStatusLabel()`
- `clearAuth()`

### 핵심 로직

1. 토큰이 없으면 로그인 화면으로 보냄
2. 사용자 정보와 현재 세션을 불러옴
3. 세션 상태에 따라 진행률 계산
4. 세션 상태에 따라 다음 버튼 링크 결정
5. 프로필 드롭다운에서 로그아웃 처리

### 구현 포인트

- 이 화면의 핵심은 "데이터 입력"이 아니라 "상태 해석"이다.
- `resolveNextStep()`이 중요하다.
  - 세션이 없으면 `basic-info.html`
  - 제출/검토 상태면 `submit-status.html`
  - 계산 완료면 `results.html`
  - 기본정보가 비어 있으면 `basic-info.html`
  - 그 외에는 `dependents.html`
- 즉 대시보드는 단순 메뉴가 아니라, 현재 업무 상태를 보고 다음 행동을 결정하는 라우팅 허브 역할을 한다.

### 공부 포인트

- 화면이 직접 비즈니스 규칙을 많이 가지지 않도록 공통 함수로 빼는 방법
- "상태 기반 UI"가 어떻게 만들어지는지

## 4-3. `basic-info.html`

### 화면 목적

- 세션의 기본 인적 정보 저장
- 다음 단계로 진행하기 위한 첫 입력 화면

### 사용한 것

- `parseBasicInfo()`
- `maskSsn()`
- `updateBasicInfo()`
- `PATCH /api/v1/tax-sessions/{sessionId}/basic-info`

### 핵심 로직

1. 페이지 진입 시 현재 세션을 가져온다
2. 세션 안의 `basicInfoJsonb`를 파싱해서 폼에 채운다
3. 사용자가 입력한 값을 다시 JSON 문자열로 만든다
4. 저장 버튼 또는 다음 단계 버튼으로 API 호출
5. 성공하면 `localStorage`의 현재 세션도 최신 값으로 교체
6. 다음 단계 선택 시 `dependents.html`로 이동

### 구현 포인트

- `basicInfoJsonb`를 화면 전용 구조처럼 사용한다.
- 주민등록번호를 그대로 저장하지 않고 `maskSsn()`으로 마스킹해서 저장한다.
- 저장과 다음 단계 이동을 `saveBasicInfo(redirectNext)` 하나로 통합했다.
  - `false`면 저장만
  - `true`면 저장 후 다음 페이지 이동

### 공부 포인트

- 폼 입력값을 백엔드 DTO에 맞는 형태로 가공하는 패턴
- 같은 저장 로직을 재사용하면서 동작만 살짝 다르게 만드는 방법

## 4-4. `dependents.html`

### 화면 목적

- 부양가족 목록 조회
- 부양가족 등록/수정/삭제
- 현재 선택된 부양가족 편집

### 사용한 것

- `listDependents()`
- `createDependent()`
- `updateDependent()`
- `deleteDependent()`
- `GET /api/v1/tax-sessions/{sessionId}/dependents`
- `POST /api/v1/tax-sessions/{sessionId}/dependents`
- `PUT /api/v1/tax-sessions/{sessionId}/dependents/{dependentId}`
- `DELETE /api/v1/tax-sessions/{sessionId}/dependents/{dependentId}`

### 핵심 로직

1. 진입 시 현재 세션 ID 확보
2. 부양가족 목록 조회
3. 왼쪽 목록 렌더링
4. 목록에서 한 명을 누르면 폼에 상세 내용 채움
5. 선택된 상태면 수정, 아니면 신규 등록
6. 삭제 시 soft delete API 호출 후 목록 갱신

### 구현 포인트

- `selectedDependentId`로 현재 편집 대상 상태를 관리한다.
- `resetDependentForm()`과 `fillDependentForm()`으로 폼 상태를 명확히 나눴다.
- `renderDependents()`가 목록 UI를 다시 그린다.
- 저장 후에는 항상 `refreshDependents()`를 호출해서 서버 기준 최신 상태를 다시 가져온다.

### 공부 포인트

- "목록 + 상세 폼" UI 패턴
- 생성과 수정이 같은 폼을 공유하는 방식
- 낙관적 갱신이 아니라 서버 재조회 방식으로 단순하게 맞추는 전략

## 4-5. `import-data.html`

### 현재 상태

- 정적 화면 중심
- 실제 API 연동은 아직 없음

### 의미

- 국세청 간소화 자료나 외부 자료 불러오기 화면을 위한 자리
- 현재는 UX 목업 성격이 강하다

### 연결 예정으로 볼 수 있는 백엔드

- 아직 직접 대응되는 프런트 연동 코드는 없다.
- 이후 구현 시 세션 기준으로 자료 import API가 추가될 가능성이 크다.

## 4-6. `income.html`

### 현재 상태

- 화면은 존재하지만 프런트 API 연동은 아직 없음

### 관련 백엔드

- `IncomeItemController`
  - 소득 항목 생성/조회/수정/삭제 API 존재

### 의미

- 백엔드 CRUD 준비는 되어 있고, 프런트 연결이 남은 상태로 보는 게 맞다.

### 공부 포인트

- "백엔드가 먼저 준비되고 프런트가 나중에 붙는 경우"를 실제로 볼 수 있다.
- 다음 단계 구현 시 `dependents.html` 패턴을 재사용할 가능성이 높다.

## 4-7. `deductions.html`

### 현재 상태

- 화면은 존재하지만 프런트 API 연동은 아직 없음

### 관련 백엔드

- `DeductionItemController`
  - 공제 항목 CRUD API 존재

### 의미

- 설계와 목업은 먼저 만들고, 실제 데이터 연결은 다음 단계로 남겨둔 상태다.

## 4-8. `evidence-docs.html`

### 현재 상태

- 화면은 존재하지만 프런트 API 연동은 아직 없음

### 관련 백엔드

- `DocumentChecklistController`
  - 증빙 체크리스트 조회 API 존재

### 의미

- 이 화면도 `dependents.html`처럼 세션별 목록을 가져와 렌더링하는 방식으로 구현될 가능성이 높다.

## 4-9. `results.html`

### 현재 상태

- 정적 결과 화면 중심
- 실제 최신 계산 결과 연동은 아직 없음

### 관련 백엔드

- `SimulationController`
  - `POST /simulation`
  - `GET /results/latest`
  - `GET /results/latest/rejections`

### 의미

- 계산 엔진 API는 준비되어 있고, 결과 화면이 그것을 아직 소비하지 않는 상태다.

## 4-10. `submit-status.html`

### 현재 상태

- 정적 상태 화면 중심
- 실제 제출 상태 연동은 아직 없음

### 관련 백엔드

- `TaxSessionController`
  - `POST /{sessionId}/submit`
- `AdminController`
  - 관리자 검토 목록/체크리스트/리뷰 API 제공

### 의미

- 제출 이후 사용자 화면과 관리자 검토 화면을 나중에 연결할 수 있도록 백엔드 기반은 어느 정도 잡혀 있다.

## 5. 인증과 보안은 어떻게 처리했는가

## 5-1. 사용 기술

- Spring Security
- JWT
- BCrypt 비밀번호 암호화
- Stateless 인증

## 5-2. 동작 방식

1. 회원가입 시 비밀번호를 BCrypt로 암호화해서 저장
2. 로그인 시 `AuthenticationManager`로 인증
3. 성공 시 access token, refresh token 발급
4. 프런트가 access token을 `localStorage`에 저장
5. 이후 `request()`가 Authorization 헤더를 자동으로 붙임
6. 서버는 `JwtAuthenticationFilter`로 사용자 인증

## 5-3. 왜 이렇게 했는가

- 서버 세션 없이도 인증 상태를 유지할 수 있다.
- 정적 HTML 기반 프런트와 잘 맞는다.
- 화면별로 토큰만 있으면 간단히 API 호출이 가능하다.

## 6. 지금 구조의 장점

- 흐름이 단순해서 디버깅이 쉽다
- 화면 단위로 파일이 분리돼 있어 찾기 쉽다
- 공통 인증/세션 로직이 `app.js`에 모여 있다
- 백엔드도 Controller / Service / Repository 구조가 분명하다
- 실제 구현된 패턴을 다음 화면에 복제해서 확장하기 좋다

## 7. 지금 구조의 한계

- HTML마다 inline script가 길어지기 쉬움
- 화면이 늘어나면 DOM 조작 코드가 반복될 수 있음
- `app.js`가 커지면 점점 역할이 많아질 수 있음
- 화면 상태 관리가 복잡해지면 프레임워크 없이 유지보수가 어려워질 수 있음

이건 잘못된 구조라는 뜻이 아니라, 현재 프로젝트 단계에서 빠르게 만들기 좋은 구조라는 뜻이다.

## 8. 1년차 개발자 관점에서 추천하는 공부 순서

1. `auth.html`을 먼저 읽는다.
2. `app.js`의 `request()`와 `initializeAuthenticatedContext()`를 읽는다.
3. `AuthController` -> `AuthService`를 따라간다.
4. `index.html`에서 `resolveNextStep()`이 어떻게 쓰이는지 본다.
5. `basic-info.html`에서 폼 데이터가 어떻게 payload로 바뀌는지 본다.
6. `dependents.html`에서 CRUD 화면 패턴을 익힌다.
7. 그 다음 `IncomeItemController`, `DeductionItemController`, `SimulationController`를 보고 아직 안 붙은 화면을 머릿속으로 설계해본다.

## 9. 이 프로젝트를 한 줄로 정리하면

이 프로젝트는 "정적 HTML 화면 + 공통 JavaScript + Spring Boot API" 조합으로 만든 연말정산 시뮬레이터이며, 현재는 인증/세션/기본정보/부양가족 화면까지 실제 데이터 흐름이 붙어 있고, 나머지 화면은 같은 패턴으로 확장할 수 있게 백엔드 기반과 목업을 먼저 준비해둔 상태다.

## 10. 다음에 보면 좋은 파일

- `backend/src/main/resources/static/auth.html`
- `backend/src/main/resources/static/index.html`
- `backend/src/main/resources/static/basic-info.html`
- `backend/src/main/resources/static/dependents.html`
- `backend/src/main/resources/static/js/app.js`
- `backend/src/main/java/com/example/yearend/user/application/AuthService.java`
- `backend/src/main/java/com/example/yearend/taxsession/application/TaxSessionService.java`
- `backend/src/main/java/com/example/yearend/taxsession/application/DependentService.java`
- `backend/src/main/java/com/example/yearend/security/SecurityConfig.java`
- `backend/src/main/java/com/example/yearend/common/exception/GlobalExceptionHandler.java`
