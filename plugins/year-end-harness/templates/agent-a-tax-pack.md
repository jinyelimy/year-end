# Agent A Tax Pack

## Context

- As-of date: <date>
- Run id: <run-id>
- Run directory: .local/harness/<date>/<run-id>
- Target law context: <law-context>
- Target tax year: <tax-year>
- Target filing year: <filing-year>
- Target rule version: <rule-version>
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

## Normalization Notes

- Rule version decision: <why this month version was chosen>
- Previous rule set: <previous version or first version>
- Diff summary: <what changed>
- Publish readiness: `READY_FOR_REVIEW | BLOCKED | PUBLISHED`

## Inferred Rules

## Open Questions

## Files

- `.local/harness/<date>/<run-id>/source-manifest.json`
- `.local/harness/<date>/<run-id>/normalized-rule-pack.json`
- `.local/harness/<date>/<run-id>/diff-from-previous.md`
- `plugins/year-end-harness/law-packs/<tax-year>/<rule-version>/...` (review 후 정본 승격 시)

## Validation

```text
=== HARNESS RESULT ===
STATUS   : warning
SUMMARY  : Replace template placeholders and validate the tax pack before handoff.
ARTIFACTS: .local/harness/<date>/<run-id>/agent-a-tax-pack.md, .local/harness/<date>/<run-id>/source-manifest.json, .local/harness/<date>/<run-id>/normalized-rule-pack.json
NEXT     : Fill the report, verify rule normalization and diff, then hand off to Agent B.
======================
```
