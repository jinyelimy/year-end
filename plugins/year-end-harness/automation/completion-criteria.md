# Completion Criteria — Gate 별 판정 규칙

이 문서는 슬라이스 워크플로의 각 Gate 에서 PASS / FAIL / HALT 를 어떻게 판정하는지 명시한다.

## Gate 0: 슬라이스 선택

| 입력 | `backlog.json` |
|------|----------------|
| 스크립트 | `pick-next-slice.py` |
| PASS | `SLICE_ID=<id>` 포함 multi-line 출력 (exit 0) |
| BACKLOG_EMPTY | `BACKLOG_EMPTY` 출력 (exit 0) |

## Gate 1: 4-1 Audit 아티팩트

| 입력 | run-dir |
|------|---------|
| PASS 조건 | `audit.md` 존재 AND 4개 기준 섹션(파싱/등록, 매칭/증빙, 계산/결과확인, 세법팩/ruleCode 연결) 모두 존재 |
| FAIL | audit.md 누락 OR 섹션 누락 |
| HALT_HUMAN_REVIEW | audit.md 에 `requires-human-confirmation: true` 플래그 존재 |

## Gate 2: 4-2 Design 아티팩트

| 입력 | run-dir |
|------|---------|
| PASS 조건 | `design.md` + `normalized-rule-pack-proposal.json` + `expected-rule-codes.txt` 3종 모두 존재 |
| FAIL | 하나라도 누락 |

## Gate 3: RuleCode 존재 확인 ⭐ 자동

| 입력 | `expected-rule-codes.txt` (run-dir 안) |
|------|---------------------------------------|
| 스크립트 | `detect-missing-rulecode.py --run-dir <RUN_DIR>` |
| PASS | `ALL_CODES_PRESENT` (exit 0) |
| SKIP | `NO_EXPECTED_CODES: ...` (exit 0) — 4-2 재실행 필요 |
| HALT_PHASE1_REENTRY | `MISSING: <codes>` (exit 1) |

## Gate 4: 구현 검증 (run-harness-gate)

| 입력 | run-dir, `--through-phase 3.5` |
|------|--------------------------------|
| 스크립트 | `plugins/year-end-harness/scripts/run-harness-gate.cmd` |
| PASS | stdout 에 `STATUS: success` OR `STATUS   : success` AND exit 0 |
| FAIL | 그 외 |

검증 내부에 포함된 체크:
- `run-parser-tests.cmd` — PDF 파서 테스트
- `run-backend-tests.cmd` — 백엔드 회귀
- `run-frontend-build.cmd` — 프론트엔드 빌드
- `validate-artifacts.py` — 아티팩트 계약 준수

## Gate 5: inferred / Open Questions 스캔

| 입력 | `validation-report.md` (run-dir 안) |
|------|-------------------------------------|
| PASS 조건 | 본문에 `inferred` 단어 0회 등장 OR 모두 해결됨 AND `Open Questions` 섹션이 빈칸이거나 모두 resolved |
| HALT_HUMAN_REVIEW | inferred 잔존 OR open blockers 존재 |

## Gate 6: 커밋 생성

| 입력 | Gate 4 + 5 PASS |
|------|-----------------|
| PASS | `git commit` exit 0 |
| FAIL | merge 충돌 또는 pre-commit hook 실패 |

## Outcome Signals 요약

내부 워크플로가 마지막 줄에 출력해야 하는 4개 시그널:

| Signal | 발동 조건 | 외부 루프 동작 |
|--------|-----------|----------------|
| `SLICE_RESULT: PASS` | Gate 4 + 5 모두 PASS | `mark-slice-done.py` + `git commit` → 다음 iteration |
| `SLICE_RESULT: HALT_HUMAN_REVIEW` | Gate 1 plag OR Gate 5 inferred/blocker | 외부 루프 종료, `completion promise = HUMAN_REVIEW_REQUIRED` |
| `SLICE_RESULT: HALT_PHASE1_REENTRY` | Gate 3 MISSING | 외부 루프 종료, `completion promise = PHASE1_REENTRY_REQUIRED` |
| `SLICE_RESULT: FAIL` | Gate 2/4/6 FAIL | 외부 루프 종료, `completion promise = FAIL` |

## 권장 `--completion-promise` 설정 (ralph)

```
BACKLOG_EMPTY OR HUMAN_REVIEW_REQUIRED OR PHASE1_REENTRY_REQUIRED OR FAIL
```

이 설정이면:
- 전체가 끝나면 `BACKLOG_EMPTY` 로 자연스럽게 종료
- 중간에 사람 검토가 필요하거나 세법팩 갱신이 필요하면 해당 지점에서 종료
- 기술적 실패 시 즉시 종료 (무한 루프 방지)
