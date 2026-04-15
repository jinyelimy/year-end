# One-Shot Autopilot — 남은 공제 슬라이스 전체 구현

이 문서는 사용자가 아래처럼 말했을 때 실행할 단일 진입 계약이다.

```text
진행되지 않은 과정을 자동화에 따라 전부 구현해줘
```

목표는 설명이나 계획 제안이 아니라, `docs/notes/command_list.md`와
`plugins/year-end-harness/automation/backlog.json` 기준으로 남은 공제 슬라이스를
우선순위 순서대로 실제 구현하고 검증하는 것이다.

## Completion Promise

루프는 아래 중 하나가 될 때만 멈춘다.

```text
BACKLOG_EMPTY
HUMAN_REVIEW_REQUIRED
PHASE1_REENTRY_REQUIRED
FAIL
```

## 시작 전 필수 확인

1. `AGENTS.md`를 따른다.
2. `docs/notes/command_list.md`를 읽고 현재 완료 별표/커밋 상태를 확인한다.
3. `plugins/year-end-harness/automation/backlog.json`을 현재 작업 큐로 사용한다.
4. `plugins/year-end-harness/automation/inner-workflow.md`를 슬라이스 1개 처리 절차로 사용한다.
5. `.local/harness/<date>/<run-id>/` 아래에 모든 산출물을 남긴다.
6. 기존 작업 트리의 사용자 변경을 되돌리지 않는다.

## 전체 루프

아래 루프를 `BACKLOG_EMPTY`, `HUMAN_REVIEW_REQUIRED`,
`PHASE1_REENTRY_REQUIRED`, `FAIL` 중 하나가 될 때까지 반복한다.

### 1. 다음 슬라이스 선택

```powershell
python plugins/year-end-harness/automation/scripts/pick-next-slice.py
```

`BACKLOG_EMPTY`가 나오면 아래를 최종 출력하고 종료한다.

```text
BACKLOG_EMPTY: all slices in scope are done
```

### 2. 슬라이스 실행

선택된 `SLICE_ID`, `NAME`, `FLOW`에 대해
`plugins/year-end-harness/automation/inner-workflow.md`를 그대로 수행한다.

기본 순서는 다음과 같다.

```text
4-1 audit
4-2 design
Gate 3: detect-missing-rulecode.py
4-3 implementation 또는 3-4 ruleset refactor
3-7 repository validation
final SLICE_RESULT
```

### 3. Phase 1 재진입 처리

Gate 3에서 `MISSING: ...`가 나오면 구현으로 넘어가지 않는다.

이 경우 Phase 1 재진입 산출물을 자동 생성한다.

```powershell
python plugins/year-end-harness/automation/scripts/prepare-phase1-reentry.py `
  --run-dir .local/harness/<date>/<run-id> `
  --validate
```

성공하면 현재 run-dir에 아래 산출물이 생긴다.

- `agent-a-tax-pack.md`
- `normalized-rule-pack.json`
- `diff-from-previous.md`
- `phase1-reentry-ready-for-review.md`

그 다음 아래 completion promise를 출력하고 종료한다.

```text
HUMAN_REVIEW_REQUIRED: <SLICE_ID> phase1-reentry-ready-for-review
```

재진입 산출물 생성 자체가 실패한 경우에만 아래를 출력한다.

```text
PHASE1_REENTRY_REQUIRED: <SLICE_ID> missing=<rule-codes>
```

세법팩 정본 승격은 자동 `PUBLISHED` 전이를 포함하지 않는다. `READY_FOR_REVIEW`
산출물 생성까지는 자동화하고, `PUBLISHED` 승인은 사람이 한다.

사람 승인은 아래 명령을 실행하는 것이다.

```powershell
python plugins/year-end-harness/automation/scripts/approve-phase1-reentry.py `
  --run-dir .local/harness/<date>/<run-id>
```

이 명령은 검토된 `READY_FOR_REVIEW` rule pack을
`plugins/year-end-harness/law-packs/<taxYear>/<ruleVersion>/`에 복사하고
정본 `normalized-rule-pack.json`의 상태를 `PUBLISHED`로 바꾼다.

### 4. 사람 검토 처리

`validation-report.md`, `normalized-rule-pack-proposal.json`, 또는 설계 산출물에
아래 중 하나가 남아 있으면 PASS 처리하지 않는다.

- `confidence: inferred`
- unresolved `open-questions`
- blocker
- 공식 소스가 확인되지 않은 세율/한도/자격 수치

이 경우 아래를 출력하고 종료한다.

```text
HUMAN_REVIEW_REQUIRED: <SLICE_ID> <reason>
```

### 5. PASS 처리

`SLICE_RESULT: PASS`인 경우에만 아래를 수행한다.

```powershell
python plugins/year-end-harness/automation/scripts/mark-slice-done.py `
  --slice-id <SLICE_ID> `
  --run-dir <RUN_DIR>
```

그 다음 한 슬라이스 단위로 커밋한다. 커밋 메시지는 AGENTS.md의 Lore Commit
Protocol을 따른다.

커밋 성공 후 다시 backlog에 commit sha를 기록한다.

```powershell
python plugins/year-end-harness/automation/scripts/mark-slice-done.py `
  --slice-id <SLICE_ID> `
  --run-dir <RUN_DIR> `
  --commit-sha <COMMIT_SHA>
```

그 다음 루프 처음으로 돌아간다.

## 병렬화 기준

한 슬라이스 안에서 서로 독립적인 조사/검증은 병렬화할 수 있다.

- 세법 ruleCode/공식 소스 검토
- 기존 구현 audit
- 테스트 영향 범위 조사
- 프론트/PDF 파서 영향 조사

하지만 아래는 순차 실행한다.

- 4-2 design 전 4-1 audit
- 4-3/3-4 전 Gate 3
- backlog 갱신 전 repository validation
- 다음 slice 전 현재 slice commit

## 금지 사항

1. `PUBLISHED` 자동 전이 금지.
2. `inferred`를 공식 소스 확인 없이 `confirmed`로 바꾸기 금지.
3. Gate 3 실패 후 구현 진행 금지.
4. `mark-slice-done.py` 외 경로로 `backlog.json` 상태 변경 금지.
5. 한 iteration에서 두 개 이상의 slice 구현 금지.
6. 검증 실패 상태에서 commit 금지.
7. 기존 사용자 변경 되돌리기 금지.

## 최종 보고 형식

종료 시 아래 중 하나를 맨 위에 쓴다.

```text
BACKLOG_EMPTY
HUMAN_REVIEW_REQUIRED
PHASE1_REENTRY_REQUIRED
FAIL
```

그 아래에 다음을 짧게 정리한다.

- 처리한 slice
- 변경한 파일
- 생성한 run-dir
- 실행한 검증
- 다음 사람이 해야 할 일
