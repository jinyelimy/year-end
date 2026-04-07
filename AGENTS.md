# AGENTS.md

## Source Of Truth

- 전체 하네스 설계: [docs/architecture/harness-engineering-design.md](docs/architecture/harness-engineering-design.md)
- 전체 프로젝트 분석: [docs/analysis/project-analysis.md](docs/analysis/project-analysis.md)
- 날짜/세법 컨텍스트: [plugins/year-end-harness/context/tax-year-context.json](plugins/year-end-harness/context/tax-year-context.json)
- Phase-focused 구현은 `harness-engineering-design.md`의 로드맵을 따른다.

## Repository Hygiene

- Codex 하네스 자산은 `plugins/year-end-harness/` 아래에 둔다.
- 로컬 플러그인 노출 설정은 `.agents/plugins/marketplace.json`에서 관리한다.
- 로컬 로그/미리보기/DB 데이터는 `.local/` 또는 `*.log` 로 관리하며 커밋하지 않는다.

---

## Working Boundaries

- 공제 정책/계산 엔진: `backend/src/main/java/com/example/yearend/deduction/`
- 공제 테스트: `backend/src/test/java/com/example/yearend/deduction/`
- 세액 계산 엔진: `backend/src/main/java/com/example/yearend/calculation/`
- PDF 가져오기 UI: `frontend/app/import-data/`, `frontend/app/deductions/`, `frontend/lib/deductionImport.js`
- 무관한 파일은 절대 수정하지 않는다.

---

## 작업 유형별 검증 루프

권장 검증 스크립트는 `plugins/year-end-harness/scripts/` 아래에 둔다. Windows 환경에서는 아래 스크립트를 우선 사용한다.

| 작업 | 권장 명령 |
|------|------|
| 파서 테스트만 | `plugins\year-end-harness\scripts\run-parser-tests.cmd` |
| 전체 백엔드 회귀 | `plugins\year-end-harness\scripts\run-backend-tests.cmd` |
| 프론트 빌드 | `plugins\year-end-harness\scripts\run-frontend-build.cmd` |
| 하네스 phase gate | `plugins\year-end-harness\scripts\run-harness-gate.cmd --run-dir .local\harness\<date>\<run-id> --through-phase <phase>` |

- backend 검증은 같은 `backend/build/` 디렉터리를 공유하므로 병렬 실행하지 않는다.
- PowerShell 환경에서는 `npm.ps1` 대신 `npm.cmd`를 사용한다.
- 하네스 산출물은 `.local/harness/<date>/<run-id>/`에 두고, 같은 날짜에 재실행할 때도 새 `run-id`를 만든다.

---

## 관찰(Observation) 프로토콜

모든 스크립트와 하네스 산출물은 아래 블록으로 종료한다.

```
=== HARNESS RESULT ===
STATUS   : success | warning | error
SUMMARY  : <한 줄 요약>
ARTIFACTS: <파일 경로>
NEXT     : <다음 권고 액션>
======================
```

- **success** → `qa-verifier` 또는 다음 phase gate 호출 권고
- **warning** → 현재 도메인 외 문제, 별도 확인 후 진행 가능
- **error** → 중단, root cause hint 확인 후 안전한 재시도

---

## 공제 규칙 추가 패턴 (가장 빈번한 작업)

```
1. DeductionType enum 에 항목 추가 (이미 있으면 건너뜀)
2. XxxDeductionPolicy 작성   ← MedicalExpenseDeductionPolicy 를 템플릿으로
3. XxxEligibilityChecker 작성 (자격 요건 있을 경우만)
4. DeductionPolicyRegistry 에 매핑 등록
5. XxxDeductionPolicyTest 작성 (경계값 최소 3개)
6. 관련 단위 테스트 + 필요 시 전체 백엔드 회귀 실행
```

**에러 복구 규칙:**
- `UNSUPPORTED_DEDUCTION_TYPE` → DeductionPolicyRegistry 매핑 확인
- 컴파일 오류 → referencePolicy 메서드 시그니처 재확인
- 동일 오류 2회 연속 → Orchestrator 에 보고하고 중단

---

## Phase 1 구현 가이드

- 홈택스 PDF 가져오기 섹션: 의료비 / 보험료 / 교육비 / 신용카드류 전체 커버
- 원시 추적 데이터는 `attributesJsonb` 에 유지 (`page`, `rawLine`, `person` 포함)
- 계산 엔진 미지원 타입은 `calculationSupported=false` 유지, 시뮬레이션 포함 금지
- 부양가족 자동 매칭은 이름 + 생년월일 키 확신 시에만
- 가족 매핑 구현은 `plugins/year-end-harness/contracts/family-mapping-contract.md`를 따른다.

---

## 현재 미구현 공제 타입 (시뮬레이션 포함 금지)

| 타입 | 상태 |
|------|------|
| `DONATION` | Policy 없음 — 시뮬레이션 시 예외 발생 |
| `CREDIT_CARD` | Policy 없음 — review-only (`calculationSupported=false`) |

이 타입들은 Policy 클래스 작성 완료 전까지 DeductionEngine 에서 건너뛰도록 처리한다.

---

## 안티패턴

- 세법 조항 확인 없이 세율/한도 수치 하드코딩
- 실제 PDF 샘플 라인 없이 파싱 정규식 수정
- Controller 통합 테스트 없이 API 시그니처 변경 후 배포
