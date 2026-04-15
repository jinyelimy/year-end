# 연말정산 하네스 공제 슬라이스 자동화

`plugins/year-end-harness/automation/` 는 `docs/notes/command_list.md`의 **Phase 3 공제 슬라이스 구현 명령**을 우선순위 순으로 자동 처리하는 2-계층 루프 구조다.

## 2-계층 구조

```
[외부 루프: ralph]              ← slice-loop.prompt.md
   │
   ├─ pick-next-slice.py 로 다음 pending 슬라이스 선택
   │
   ├─ [내부 워크플로: ultrawork / codex session]   ← inner-workflow.md
   │     ├─ 4-1 audit   (코드 수정 없음)
   │     ├─ 4-2 design  (코드 수정 없음)
   │     ├─ Gate: detect-missing-rulecode.py
   │     │     └─ MISSING → HALT_PHASE1_REENTRY
   │     ├─ 4-3 impl OR 3-4 refactor
   │     ├─ run-harness-gate.cmd --through-phase 3.5
   │     └─ SLICE_RESULT: PASS | HALT_* | FAIL
   │
   ├─ PASS: mark-slice-done.py + git commit → 다음 iteration
   └─ HALT/FAIL: completion promise 출력 → 루프 종료
```

내부 워크플로는 **에이전트 메커니즘 중립**이다 — ralph의 inner, ultrawork, 단일 codex 세션, Claude Code 어느 쪽이든 돌아간다.

## 디렉토리 구조

```
automation/
├── README.md                      # 이 문서
├── backlog.json                   # 슬라이스 큐 (상태 포함)
├── slice-loop.prompt.md           # 외부 루프 진입 프롬프트 (ralph)
├── inner-workflow.md              # 슬라이스 1개 처리 절차 (ultrawork)
├── completion-criteria.md         # PASS/FAIL/HALT 판정 규칙
└── scripts/
    ├── pick-next-slice.py         # 다음 pending 슬라이스 선택
    ├── mark-slice-done.py         # 완료 표시 + runHistory 기록
    ├── check-backlog-empty.py     # 남은 슬라이스 확인
    └── detect-missing-rulecode.py # Phase 1 재진입 감지
```

## 실행 방법

### 자연어 원샷 명령 (Codex 세션)

앞으로 Codex에 아래처럼 말하면 된다.

```text
진행되지 않은 과정을 자동화에 따라 전부 구현해줘
```

이 요청은 `AGENTS.md`의 원샷 공제 자동화 트리거를 통해
`one-shot-autopilot.md` 계약으로 연결된다. 루프는 남은 backlog를 우선순위대로
처리하다가 `BACKLOG_EMPTY`, `HUMAN_REVIEW_REQUIRED`,
`PHASE1_REENTRY_REQUIRED`, `FAIL` 중 하나에서만 멈춘다.

### Windows 스크립트 원샷

PowerShell에서는 `omx.ps1` 실행 정책에 막힐 수 있으므로 `.cmd`를 사용한다.
기본 실행은 비대화형 `omx exec` 모드다.

```cmd
plugins\year-end-harness\scripts\run-deduction-autopilot.cmd
```

실제 터미널에서 Ralph persistence 모드로 오래 돌리고 싶으면:

```cmd
plugins\year-end-harness\scripts\run-deduction-autopilot.cmd --ralph
```

현재 backlog 상태만 보려면:

```cmd
plugins\year-end-harness\scripts\run-deduction-autopilot.cmd --status
```

### Codex CLI + ralph (권장: 전체 backlog 드레인)

```text
/ralph-loop @plugins/year-end-harness/automation/slice-loop.prompt.md \
  --completion-promise "BACKLOG_EMPTY"
```

### Codex CLI + ultrawork (한 슬라이스만 심층)

```text
/ultrawork @plugins/year-end-harness/automation/inner-workflow.md \
  --var NAME="인적공제" --var SLICE_ID="personal-deduction"
```

### 수동 / 테스트 (사람이 오케스트레이션)

```bash
# 1. 다음 슬라이스 확인
python plugins/year-end-harness/automation/scripts/pick-next-slice.py

# 2. 출력된 SLICE_ID/NAME/FLOW 를 받아 inner-workflow.md 에 따라 에이전트에 전달

# 3. PASS 완료 후 상태 갱신
python plugins/year-end-harness/automation/scripts/mark-slice-done.py \
  --slice-id personal-deduction \
  --run-dir .local/harness/2026-04-16/20260416-090000-personal-deduction

# 4. 남은 slice 개수 확인
python plugins/year-end-harness/automation/scripts/check-backlog-empty.py
```

## 자동화 범위 (현재 등록)

**P0 잔여 (3)** — 반드시 먼저 구현
- `personal-deduction` — 인적공제 (본인/배우자/부양가족/경로우대/장애인/부녀자/한부모)
- `pension-insurance-premium` — 연금보험료공제 (국민연금 등)
- `social-insurance-special` — 특별소득공제 사회보험료 (건강/고용)

**P1 (5)** — 파서 연결이 가까운 것부터
- `credit-card` — 신용카드 등 사용금액 소득공제
- `donation` — 기부금 세액공제
- `insurance-premium-ext` — 보험료 세액공제 확장 (장애인전용보장성)
- `medical-ext` — 의료비 세액공제 확장 (난임/미숙아/장애인/고령자)
- `education-ext` — 교육비 세액공제 확장 (국외/학자금대출/취학전/교복)

**완료 (3, 참고)**
- `earned-income-deduction` — 근로소득공제 (commit `169f77f`)
- `income-tax-rate-table` — 실제 세율표 (commit `1bbc1fc`)
- `earned-income-tax-credit` — 근로소득세액공제 (commit `1bbc1fc`)

## HALT 조건 (사람 개입 필요)

| Signal | 의미 | 후속 조치 |
|--------|------|-----------|
| `HALT_HUMAN_REVIEW` | validation-report 에 `inferred` 값 잔존 or open blockers | 사람이 해당 ruleCode를 confirmed 로 확정 후 resume |
| `HALT_HUMAN_REVIEW` | Gate 3 누락 후 Phase 1 재진입 산출물이 `READY_FOR_REVIEW`까지 자동 생성됨 | 사람이 정본 승격/게시 승인 후 resume |
| `HALT_PHASE1_REENTRY` | 세법팩에 예상 ruleCode가 없고 자동 재진입 산출물 생성도 실패 | 오류를 고친 뒤 `prepare-phase1-reentry.py` 재실행 |
| `FAIL` | 빌드/테스트 실패 또는 예기치 않은 에러 | 수동 디버깅 |

Gate 3에서 ruleCode가 누락되면 자동화는 아래 명령을 실행해 같은 run-dir에
Phase 1 산출물을 만든다.

```cmd
python plugins/year-end-harness/automation/scripts/prepare-phase1-reentry.py ^
  --run-dir .local/harness/<date>/<run-id> ^
  --validate
```

생성되는 파일:

- `agent-a-tax-pack.md`
- `normalized-rule-pack.json`
- `diff-from-previous.md`
- `phase1-reentry-ready-for-review.md`

## 사람 승인 / 정본 게시 명령

여기서 말하는 "승인"은 별도 UI가 아니라, 사람이 `READY_FOR_REVIEW` 산출물을
검토한 뒤 아래 명령을 실행해 정본 law pack으로 게시하는 것을 뜻한다.

```cmd
python plugins/year-end-harness/automation/scripts/approve-phase1-reentry.py ^
  --run-dir .local/harness/<date>/<run-id>
```

이 명령은 다음을 수행한다.

- `normalized-rule-pack.json`의 모든 규칙이 `confidence: confirmed`인지 확인
- `plugins/year-end-harness/law-packs/<taxYear>/<ruleVersion>/` 생성
- `agent-a-tax-pack.md`, `source-manifest.json`, `normalized-rule-pack.json`, `diff-from-previous.md` 복사
- 정본 `normalized-rule-pack.json` 상태를 `PUBLISHED`로 변경
- `approval-manifest.json` 기록
- 정본 산출물 contract validation 실행

이미 정본 경로가 있으면 기본적으로 실패한다. 의도적으로 덮어쓸 때만 `--force`를 사용한다.

## 절대 금지

1. **자동 `PUBLISHED` 전이 금지** — `READY_FOR_REVIEW` → `PUBLISHED` 는 사람만 수행
2. **`inferred` → `confirmed` 자동 변환 금지** — 공식 소스 재검증 없이 상태 변경 불가
3. **세법팩 자동 수정 금지** — ruleCode 누락 시 HALT_PHASE1_REENTRY 로 사람 호출
4. **`backlog.json` 직접 편집 금지** — `mark-slice-done.py` 만 사용
5. **`run-id` 변경 금지** — 한 슬라이스의 모든 산출물은 같은 run-dir 에 모아야 함
