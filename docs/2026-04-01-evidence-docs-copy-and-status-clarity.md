# 2026-04-01 4단계 증빙 화면 문구와 상태 표현 정리 기록

## 작업 개요

이번 작업은 프론트 4단계 `증빙 서류 관리` 화면에서
사용자가 가장 헷갈리기 쉬운 세 가지 상태를 **서로 다른 의미로 분리해서 보여주도록 정리**한 작업이다.

그 세 가지는 아래다.

- `requiredYn`: 이 서류가 필요한가
- `submittedYn`: 사용자가 자료를 제출했는가
- `reviewStatus`: 제출된 서류를 검토한 현재 상태는 무엇인가

기존 화면은 이 값들이 한 줄에 섞여 보이거나,
하나의 상태 배지만 더 눈에 띄어서 사용자가 의미를 혼동할 수 있었다.

특히 홈택스 import 흐름이 들어오면서 아래 혼란이 생기기 쉬웠다.

- deductions 화면의 imported item reviewStatus
- evidence-docs 화면의 document checklist reviewStatus

둘은 비슷한 단어를 쓰지만, 완전히 같은 개념은 아니다.

이번 작업의 목표는 이 차이를 화면 문구와 배지 표현으로 분명히 만드는 것이었다.

## 왜 이 작업이 필요했는가

예를 들어 사용자가 보험료 항목을 가져왔다고 하자.

그때 화면에서 아래 세 질문은 서로 다르다.

1. 보험료 증빙이 필요한가
2. 자료가 실제로 제출되었는가
3. 제출된 자료가 검토 완료되었는가

이 세 질문은 모두 “상태”처럼 보이지만,
실제로는 서로 다른 층위의 정보다.

신입 개발자 입장에서 쉽게 비유하면:

- `requiredYn`은 체크리스트 생성 규칙
- `submittedYn`은 사용자 입력/연동 여부
- `reviewStatus`는 검토 프로세스 상태

즉 같은 화면에 보여도 역할이 다르다.

이 차이를 화면이 설명해 주지 않으면,
사용자는 아래처럼 오해할 수 있다.

- 제출되었으면 검토도 끝난 줄 안다
- 필수 서류면 이미 제출된 줄 안다
- 공제항목 검토 상태와 증빙 체크리스트 검토 상태를 같은 것으로 안다

그래서 이번 작업에서는 기능 추가보다 “의미 분리”를 우선했다.

## 이번에 바뀐 파일과 역할

### 1. 공통 뷰 헬퍼 보강

파일:

- `frontend/lib/yearEndView.js`

이번에 추가한 helper는 아래 3개다.

- `getChecklistRequirementMeta(requiredYn)`
- `getChecklistSubmissionMeta(submittedYn)`
- `getChecklistReviewMeta(status)`

왜 이 파일에 넣었는가:

- 여러 화면에서 같은 라벨/배지 규칙을 재사용하기 쉬워진다
- 페이지 파일 안에 if/else가 너무 많아지는 것을 막는다
- “상태를 어떻게 보여줄지”라는 UI 규칙을 한 군데로 모을 수 있다

즉 역할을 나누면 아래와 같다.

- `page.js`: 화면 배치와 렌더링
- `yearEndView.js`: 상태 텍스트와 배지 스타일 결정

### 2. 실제 화면 페이지 수정

파일:

- `frontend/app/evidence-docs/page.js`

이 파일에서는 아래를 바꿨다.

- 상단 제목을 `전체 준비율`에서 `증빙 검토 진행률`에 더 가깝게 정리
- 진행률이 “제출률”이 아니라 “검토 완료 기준”임을 설명
- 안내 카드에서 `필수/선택`, `제출 여부`, `검토 상태`의 의미를 따로 설명
- 각 체크리스트 카드 안에서도 세 상태를 별도 배지로 분리
- `검토일` 문구를 `증빙 검토일`로 조금 더 명확하게 수정

## 코드 흐름 기준으로 보면 어떻게 동작하는가

### 1. API는 checklist 데이터를 내려준다

이 화면의 데이터 출발점은 아래다.

- `listDocumentChecklists(...)`

즉 이 화면은 `DeductionItem` 자체가 아니라 `DocumentChecklist` 응답을 본다.

그래서 여기서의 `reviewStatus`는
공제 항목 상태가 아니라 **문서 체크리스트 상태**라고 보는 것이 맞다.

### 2. page.js가 checklist 값을 받아 meta helper로 넘긴다

예를 들면 각 항목 렌더링에서 이런 흐름이 생긴다.

```javascript
const requirementMeta = getChecklistRequirementMeta(item.requiredYn);
const submissionMeta = getChecklistSubmissionMeta(item.submittedYn);
const reviewMeta = getChecklistReviewMeta(item.reviewStatus);
```

즉 원시 boolean/string 값을 바로 화면에 뿌리지 않고,
사람이 읽기 쉬운 라벨과 색상 정보로 변환하는 중간 단계가 생긴 것이다.

### 3. helper는 텍스트와 배지 스타일을 결정한다

예를 들어 `submittedYn`은 아래처럼 변환된다.

```javascript
submittedYn === true
  ? { label: "자료 제출됨", badgeClass: "bg-sky-100 text-sky-700" }
  : { label: "자료 미제출", badgeClass: "bg-slate-100 text-slate-600" }
```

이 구조의 장점은 아래와 같다.

- 화면 코드가 단순해진다
- 다른 화면에서도 같은 상태 표현을 재사용할 수 있다
- 상태 라벨을 바꾸고 싶을 때 helper만 보면 된다

### 4. 카드 렌더링에서 세 상태를 따로 보여 준다

이제 체크리스트 카드 안에는 아래 세 배지가 함께 보인다.

- `필수 서류` 또는 `선택 서류`
- `자료 제출됨` 또는 `자료 미제출`
- `검토 상태 · 확인됨/검토 대기/보완 필요`

이 변경의 중요한 의미는,
사용자가 한 카드에서 아래를 동시에 읽을 수 있다는 점이다.

```text
이 서류가 필요한가?
자료가 들어왔는가?
검토는 끝났는가?
```

기존에는 이 질문들이 섞여 보였다면,
지금은 서로 다른 배지로 분리되어 읽힌다.

## 예시로 보면 무엇이 달라졌는가

### 예시 1. 필수지만 아직 제출 안 된 서류

값:

```text
requiredYn = true
submittedYn = false
reviewStatus = PENDING
```

이제 화면에서는 아래처럼 이해하기 쉬워진다.

- 필수 서류
- 자료 미제출
- 검토 상태 · 검토 대기

여기서 사용자는 “아직 제출도 안 했으니 검토가 대기인 게 자연스럽다”고 이해할 수 있다.

### 예시 2. 제출은 되었지만 검토가 끝나지 않은 서류

값:

```text
requiredYn = true
submittedYn = true
reviewStatus = PENDING
```

이 상태는 이전보다 훨씬 명확하게 읽힌다.

- 서류는 들어왔다
- 하지만 검토는 아직 끝나지 않았다

즉 `제출 여부`와 `검토 상태`가 서로 다름을 화면이 직접 설명한다.

### 예시 3. deductions import 상태와의 구분

홈택스 import 항목에서 `reviewStatus = PENDING`은
“공제 항목 자체가 아직 검토 전”이라는 의미일 수 있다.

반면 evidence-docs 화면의 `reviewStatus = PENDING`은
“증빙 체크리스트 검토가 아직 대기”라는 의미다.

이번 화면 수정은 이 차이를 안내 문구로 드러낸다.

상단 설명 카드에 이런 메시지를 넣은 이유도 바로 이것이다.

- 이 화면에서는 공제항목이 아니라 증빙 체크리스트 상태를 보여준다

## 어떤 코드를 먼저 읽으면 좋은가

추천 순서는 아래와 같다.

1. `frontend/lib/yearEndView.js`
2. `frontend/app/evidence-docs/page.js`
3. `backend/src/main/java/com/example/yearend/document/application/DocumentChecklistService.java`

왜 백엔드 파일도 같이 보라고 하는가:

- 프론트가 보여 주는 `requiredYn`, `submittedYn`, `reviewStatus`는 결국 백엔드에서 채운 값이기 때문이다
- 즉 프론트 문구를 이해하려면 값의 생성 위치도 같이 보는 것이 좋다

## 이번 작업에서 새 라이브러리를 추가했는가

아니다.

이번 작업은 기존 프론트 구조 안에서 정리한 것이다.

- 새 라이브러리 추가 없음
- 새 폴더 추가 없음
- 변경 위치는 기존 `frontend/app`, `frontend/lib` 안

이 점도 실무에서 중요하다.

- 모든 문제를 새 라이브러리로 푸는 것은 아니다
- 단순한 표현 개선은 기존 helper와 page 구조를 잘 정리하는 것이 더 좋은 경우가 많다

## 검증 결과

확인한 내용은 아래와 같다.

- `frontend\\npm.cmd run build` 통과
- `/evidence-docs` 페이지 빌드 포함 전체 정적 페이지 생성 성공

즉 문구와 상태 helper를 추가했지만 빌드는 깨지지 않았다.

## 이번 작업의 효과

이번 변경으로 기대하는 효과는 아래와 같다.

- 사용자가 `필수 여부`, `제출 여부`, `검토 상태`를 혼동할 가능성이 줄었다
- evidence-docs 화면의 `reviewStatus`가 무엇을 뜻하는지 더 분명해졌다
- deductions import 상태와 document checklist 상태를 같은 것으로 오해할 가능성이 줄었다
- 같은 상태 표현을 helper로 모아 두어서 이후 수정이 쉬워졌다

## 아직 남아 있는 한계

아직 남아 있는 개선 여지는 있다.

- 실제 `보기` 버튼 상세 동작은 아직 비어 있다
- 제출 경로가 여러 종류가 되면 `submittedYn`만으로는 부족할 수 있다
- reviewStatus 이력이나 reviewer 정보까지 보여 주는 UI는 아직 없다

그래도 지금 단계에서는 핵심이 명확하다.

- 사용자가 상태 의미를 헷갈리지 않게 만드는 것

이번 작업은 바로 그 읽기 경험을 정리한 단계라고 보면 된다.
