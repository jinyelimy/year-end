# Validation Matrix

## Default Commands

- Parser-focused change
  - `plugins\year-end-harness\scripts\run-parser-tests.cmd`
- Backend calculation or policy change
  - `plugins\year-end-harness\scripts\run-backend-tests.cmd`
- Frontend import/review UI change
  - `plugins\year-end-harness\scripts\run-frontend-build.cmd`

## Selection Rules

1. `backend/src/main/java/com/example/yearend/deduction/` 변경 시 parser tests부터 시작한다.
2. `backend/src/main/java/com/example/yearend/calculation/` 변경 시 full backend regression을 포함한다.
3. `frontend/app/import-data/`, `frontend/app/deductions/`, `frontend/lib/deductionImport.js` 변경 시 frontend build를 포함한다.
4. parser tests와 full backend regression은 동시에 돌리지 않는다.
5. 결과는 validation report에 명령, 작업 디렉터리, exit code, 메모를 남긴다.
