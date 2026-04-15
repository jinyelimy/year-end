# Inner Workflow — 공제 슬라이스 1개 처리 절차

이 문서는 **공제 1개**를 Phase 0~3.5 절차에 따라 PASS 까지 가져가는 상세 절차다.
**에이전트 메커니즘 중립** — ralph inner, ultrawork, 단일 codex 세션, Claude Code 어디서든 돌아간다.

## 입력 변수

| 변수 | 의미 | 예시 |
|------|------|------|
| `SLICE_ID` | `backlog.json` 의 slice.id | `personal-deduction` |
| `NAME` | `[공제명]` | `인적공제` |
| `FLOW` | 수행할 step 배열 | `["4-1", "4-2", "4-3"]` |

## 출력

```
.local/harness/<YYYY-MM-DD>/<timestamp>-<SLICE_ID>-<step>/
├── audit.md OR design.md OR agent-c-implementation-notes.md
├── source-manifest.json             # 참고한 공식 소스 / 기존 run-dir 목록
├── normalized-rule-pack-proposal.json  # 4-2 에서 제안 (ruleCode 포함)
├── expected-rule-codes.txt          # 4-2 결과물, Gate 3 에서 사용
├── validation-report.md             # 4-3/3-4 후 필수
├── parser-tests.log
├── backend-regression.log
├── frontend-build.log
└── artifact-validation.log
```

## 워크플로

### Step A: 준비

1. 오늘 날짜를 `YYYY-MM-DD` (Asia/Seoul) 로 확정
2. Run-ID: `<YYYYMMDD-HHMMSS>-<SLICE_ID>-<step>` 형식
3. Run-dir 생성: `.local/harness/<date>/<run-id>/`
4. `source-manifest.json` 에 참고한 공식 소스/이전 run-dir 메타 기록

### Step B: FLOW 순차 실행

`FLOW` 배열의 각 step 을 순서대로 수행한다. 각 step 별 별도 run-id 를 만들 수도, 같은 run-id 안에서 이어갈 수도 있으나 **한 슬라이스 = 한 commit** 원칙을 지킨다.

#### Step 4-1: 기존 구현 상태 점검 (READ-ONLY)

**프롬프트 (agent 에 전달):**

```
하네스 기준으로 [NAME]의 현재 구현 상태를 점검해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
이미 구현된 부분과 누락된 부분을 `파싱/등록`, `매칭/증빙`, `계산/결과확인`, `세법팩/ruleCode 연결` 기준으로 나눠 정리해줘.
기존 구현은 최대한 재사용하고, 추가 설계가 필요한 부분만 제안해줘.
산출물은 `.local/harness/<date>/<run-id>/`에 남겨줘.
```

**완료 체크:**
- [ ] `audit.md` 생성됨
- [ ] 4개 기준(파싱/등록, 매칭/증빙, 계산/결과확인, 세법팩/ruleCode 연결) 섹션이 모두 있음
- [ ] 누락된 `ruleCode` 후보 목록이 있음
- [ ] Phase 3 에서 어느 flow (`4-3` 신규 vs `3-4` 리팩터) 가 맞는지 결정됨

#### Step 4-2: 설계 (READ-ONLY)

**프롬프트:**

```
하네스 기준으로 [NAME] 설계를 진행해줘.
Phase 0부터 2까지만 수행하고 코드 수정은 하지 마.
입력 소스, 공제 항목 등록, 부양가족 매칭, 증빙자료, 결과확인 계산, 그리고 월별 세법팩에서 어떤 ruleCode를 써야 하는지까지 함께 설계해줘.
산출물은 `.local/harness/<date>/<run-id>/`에 남겨줘.
```

**추가 산출물 필수:**
- `normalized-rule-pack-proposal.json` — 이 슬라이스에서 쓸 ruleCode 와 파라미터 구조 제안 (각 규칙에 `confidence: confirmed | inferred` 라벨)
- `expected-rule-codes.txt` — 한 줄에 하나씩 ruleCode 나열 (주석은 `#` 으로 시작). Gate 3 에서 이 파일을 읽는다.

**완료 체크:**
- [ ] `design.md` 생성됨
- [ ] `normalized-rule-pack-proposal.json` 생성됨
- [ ] `expected-rule-codes.txt` 생성됨
- [ ] `inferred` 라벨이 붙은 규칙이 있다면 `open-questions` 섹션에 근거 질문 명시

#### Gate 3: 세법팩 ruleCode 존재 확인 (자동)

4-2 직후 반드시 실행:

```bash
python plugins/year-end-harness/automation/scripts/detect-missing-rulecode.py \
  --run-dir .local/harness/<date>/<run-id>
```

**결과 해석:**

| 출력 | 판정 | 다음 동작 |
|------|------|-----------|
| `ALL_CODES_PRESENT` | PASS | Step 4-3 또는 3-4 로 진행 |
| `NO_EXPECTED_CODES: ...` | SKIP | 4-2 가 expected-rule-codes.txt 를 만들지 않음 — 4-2 재실행 |
| `MISSING: <codes>` | RE-ENTRY | 즉시 Phase 1 재진입 자동 산출물을 생성한 뒤 사람 검토 대기로 종료 |

누락 ruleCode가 있으면 아래 명령을 자동 실행한다.

```bash
python plugins/year-end-harness/automation/scripts/prepare-phase1-reentry.py \
  --run-dir .local/harness/<date>/<run-id> \
  --validate
```

이 명령은 같은 run-dir 안에 아래 산출물을 만든다.

- `agent-a-tax-pack.md`
- `normalized-rule-pack.json`
- `diff-from-previous.md`
- `phase1-reentry-ready-for-review.md`

자동화는 여기서 `PUBLISHED` 전이를 하지 않는다. 재진입 산출물 생성 후 아래를 출력하고 종료한다.

```
SLICE_RESULT: HALT_HUMAN_REVIEW slice-id=<SLICE_ID> reason=phase1-reentry-ready-for-review
```

#### Step 4-3: 신규 구현 (실제 코드 수정)

FLOW 에 `4-3` 이 포함된 경우 (Policy 클래스가 아직 없을 때).

**프롬프트:**

```
방금 만든 설계를 기준으로 하네스 기준 [NAME] 구현을 진행해줘.
Phase 3부터 3.5까지만 수행해줘.
가능하면 `파싱/등록`, `매칭/증빙`, `계산/결과확인` 중 한 슬라이스만 선택해서 진행해줘.
필요한 parser tests / backend regression / frontend build를 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.
```

#### Step 3-4: 정책 클래스 상수 제거 (리팩터)

FLOW 에 `3-4` 혹은 `3-4-or-4-3` 가 포함되고 기존 Policy 가 존재할 때.

**프롬프트:**

```
하네스 기준으로 [NAME] 정책 클래스의 하드코딩 숫자를 룰셋 기반으로 치환해줘.
Phase 3부터 3.5까지만 수행해줘.
정책 클래스가 상수 대신 `RuleSnapshot` 또는 동등한 읽기 모델에서 한도/공제율/효력일을 읽게 바꿔줘.
필요한 backend regression을 수행하고 validation report를 남겨줘.
산출물은 같은 `run-id` 아래에 남겨줘.
```

#### `3-4-or-4-3` 결정 로직

| 상황 | 선택 |
|------|------|
| Policy 클래스가 없음 | `4-3` |
| Policy 클래스가 있고 하드코딩 상수만 있음 | `3-4` |
| Policy 클래스가 있지만 subtype 확장이 주 목적 | 하드코딩 제거는 `3-4`, subtype 추가는 `4-3` 를 순차 |

audit (4-1) 단계에서 이 결정을 기록해야 한다.

### Step C: 저장소 검증 Gate (4-3/3-4 후 필수)

```bash
plugins\year-end-harness\scripts\run-harness-gate.cmd ^
  --run-dir .local\harness\<date>\<run-id> ^
  --through-phase 3.5
```

또는 파이썬 버전:
```bash
python plugins/year-end-harness/scripts/run-harness-gate.py \
  --run-dir .local/harness/<date>/<run-id> \
  --through-phase 3.5
```

**결과 해석:**
- `STATUS: success` → Step D 로
- 그 외 → `SLICE_RESULT: FAIL slice-id=<SLICE_ID> reason=harness-gate-failed`

### Step D: 최종 판정

`validation-report.md` 를 스캔:

| 조건 | 판정 |
|------|------|
| inferred 값 잔존 | `SLICE_RESULT: HALT_HUMAN_REVIEW slice-id=<SLICE_ID> reason=inferred-values-remain` |
| Open Questions 에 blocker 항목 존재 | `SLICE_RESULT: HALT_HUMAN_REVIEW slice-id=<SLICE_ID> reason=open-blockers` |
| 모두 clear | `SLICE_RESULT: PASS slice-id=<SLICE_ID> run-dir=<RUN_DIR>` |

## 출력 형식 (반드시 마지막 줄)

정확히 아래 4가지 중 하나:

```
SLICE_RESULT: PASS slice-id=<id> run-dir=<path>
SLICE_RESULT: HALT_HUMAN_REVIEW slice-id=<id> reason=<text>
SLICE_RESULT: HALT_PHASE1_REENTRY slice-id=<id> missing=<comma-separated-codes>
SLICE_RESULT: FAIL slice-id=<id> reason=<text>
```

`HALT_PHASE1_REENTRY`는 재진입 자동 산출물 생성 자체가 실패했을 때만 사용한다.

## 절대 금지

1. 자동 `PUBLISHED` 전이
2. `inferred` → `confirmed` 자체 변환
3. 세법팩 (`plugins/year-end-harness/law-packs/**`) 자체 수정
4. `backlog.json` 직접 수정
5. `run-id` 변경 (한 슬라이스의 모든 산출물 = 같은 run-dir)
6. Gate PASS 없이 commit
