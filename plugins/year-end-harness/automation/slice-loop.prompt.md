# Slice Loop — 외부 루프 프롬프트 (ralph용)

이 프롬프트는 ralph 가 매 iteration 마다 동일하게 feed 하는 외부 루프다.
한 iteration 에서 정확히 **슬라이스 1개**를 처리하고, 결과에 따라 완료 신호를 출력한다.

## 당신의 역할

`plugins/year-end-harness/automation/backlog.json` 의 공제 슬라이스를 우선순위 순으로 하나씩 PASS 시킨다. `docs/notes/command_list.md` 의 원칙과 명령 템플릿을 따른다.

## 한 iteration 에 할 일

### 1. 다음 pending 슬라이스 선택

```bash
python plugins/year-end-harness/automation/scripts/pick-next-slice.py
```

출력 예:
```
SLICE_ID=personal-deduction
NAME=인적공제
PRIORITY=P0
CATEGORY=공제 슬라이스
FLOW=4-1,4-2,4-3
NOTES=기본 인적공제와 추가공제(경로우대/장애인/부녀자/한부모)를 subType으로 분리
```

또는:
```
BACKLOG_EMPTY
```

### 2. BACKLOG_EMPTY 처리

즉시 아래를 출력하고 종료:
```
BACKLOG_EMPTY: all slices in scope are done
```

ralph 의 completion-promise 가 `BACKLOG_EMPTY` 면 루프가 정상 종료된다.

### 3. 슬라이스가 있으면 inner-workflow 수행

`plugins/year-end-harness/automation/inner-workflow.md` 의 전체 절차를 위 `NAME`/`SLICE_ID`/`FLOW` 에 대해 수행한다. 그 문서의 마지막에 아래 중 하나의 `SLICE_RESULT` 를 출력해야 한다.

### 4. SLICE_RESULT 에 따른 후속 처리

#### PASS

```bash
# 1) backlog.json 갱신
python plugins/year-end-harness/automation/scripts/mark-slice-done.py \
  --slice-id <SLICE_ID> \
  --run-dir <RUN_DIR>

# 2) git commit (한 슬라이스 = 한 커밋 원칙)
git add -A
git commit -m "feat(<SLICE_ID>): <NAME> 슬라이스 구현 완료

Harness run-dir: <RUN_DIR>
Flow: <FLOW 내역>
Gate: STATUS success"

# 3) commit sha 를 backlog 에 기록
python plugins/year-end-harness/automation/scripts/mark-slice-done.py \
  --slice-id <SLICE_ID> \
  --run-dir <RUN_DIR> \
  --commit-sha $(git rev-parse HEAD)
```

그 다음 다음 iteration 으로 넘어간다 (ralph 가 같은 프롬프트를 다시 feed 하면 1번으로 돌아감).

#### HALT_HUMAN_REVIEW

```
HUMAN_REVIEW_REQUIRED: <SLICE_ID> <reason>
```

ralph 의 completion-promise 를 `HUMAN_REVIEW_REQUIRED` 로 설정했다면 종료된다. 사람이 검토 후 수동 resume.

#### HALT_PHASE1_REENTRY

```
PHASE1_REENTRY_REQUIRED: <SLICE_ID> missing=<rule-codes>
```

이 신호는 Gate 3 누락 자체가 아니라, `prepare-phase1-reentry.py`가 Phase 1 재진입
산출물 생성에 실패했을 때만 사용한다. Gate 3 누락이 감지되면 먼저 아래를 실행해야 한다.

```bash
python plugins/year-end-harness/automation/scripts/prepare-phase1-reentry.py \
  --run-dir <RUN_DIR> \
  --validate
```

성공하면 `PHASE1_REENTRY_REQUIRED` 대신 아래처럼 사람 검토 대기로 종료한다.

```
HUMAN_REVIEW_REQUIRED: <SLICE_ID> phase1-reentry-ready-for-review
```

#### FAIL

```
FAIL: <SLICE_ID> <reason>
```

기술적 실패. 디버깅 필요.

## 금지 사항 (위반 시 즉시 FAIL)

- `backlog.json` 을 `mark-slice-done.py` 외의 경로로 수정 금지
- 한 iteration 에서 2개 이상의 슬라이스 처리 금지 (ralph 가 다시 불러줌)
- `READY_FOR_REVIEW` 룰셋을 자체 판단으로 `PUBLISHED` 로 전이 금지
- `inferred` 값이 남아있는 상태에서 PASS 판정 금지
- 세법팩 (`plugins/year-end-harness/law-packs/**`) 직접 수정 금지 — ruleCode 누락 시 HALT

## Completion Promise 매핑

ralph `--completion-promise` 값에 따라 종료 조건이 다르다:

| completion-promise | 언제 종료되는가 |
|--------------------|-----------------|
| `BACKLOG_EMPTY` | 모든 슬라이스 done (권장, 기본값) |
| `HUMAN_REVIEW_REQUIRED` OR `PHASE1_REENTRY_REQUIRED` OR `BACKLOG_EMPTY` | 사람 개입이 필요하거나 전부 끝날 때 (실전 권장) |
| `FAIL` | 실패 시에만 (디버깅 모드) |

실전에서는 다음 문자열을 completion-promise 로 권장:

```
BACKLOG_EMPTY OR HUMAN_REVIEW_REQUIRED OR PHASE1_REENTRY_REQUIRED OR FAIL
```
