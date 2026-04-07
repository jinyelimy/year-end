# Agent A Tax Pack

## Context

- As-of date: <date>
- Run id: <run-id>
- Run directory: .local/harness/<date>/<run-id>
- Target law context: <law-context>
- Requested scope: <requested-scope>

## Inputs

- AGENTS.md
- docs/architecture/harness-engineering-design.md
- plugins/year-end-harness/context/tax-year-context.json
- Official source set

## Source Register

| Source | URL | Published At | Effective At | Checked At | Notes |
|---|---|---|---|---|---|
| <source> | <url> | <published-at> | <effective-at> | <checked-at> | <notes> |

## Confirmed Rules

### 세율표

### 공제 한도표

### 인적공제 판단표

### 항목별 가족 합산 가능 여부 표

### 증빙 요구사항 표

## Inferred Rules

## Open Questions

## Files

## Validation

```text
=== HARNESS RESULT ===
STATUS   : warning
SUMMARY  : Replace template placeholders and validate the tax pack before handoff.
ARTIFACTS: .local/harness/<date>/<run-id>/agent-a-tax-pack.md
NEXT     : Fill the report, run artifact validation, then hand off to Agent B.
======================
```
