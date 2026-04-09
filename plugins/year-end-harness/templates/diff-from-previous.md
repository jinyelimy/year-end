# Rule Diff

## Context

- As-of date: <date>
- Run id: <run-id>
- Run directory: .local/harness/<date>/<run-id>
- Target law context: <law-context>
- Current rule version: <current-rule-version>
- Previous rule version: <previous-rule-version-or-first-version>

## Inputs

- `.local/harness/<date>/<run-id>/source-manifest.json`
- `.local/harness/<date>/<run-id>/normalized-rule-pack.json`
- `plugins/year-end-harness/law-packs/<tax-year>/<previous-rule-version>/normalized-rule-pack.json` or `first version`

## Version Pair

- Current rule set id: <current-rule-set-id>
- Previous rule set id: <previous-rule-set-id-or-first-version>
- Change summary: <summary>

## Added Rules

| Rule Code | Deduction Type | Summary | Source Refs |
|---|---|---|---|
| <rule-code> | <deduction-type> | <summary> | <source-refs> |

## Changed Rules

| Rule Code | Change Type | Impact | Source Refs |
|---|---|---|---|
| <rule-code> | <change-type> | <impact> | <source-refs> |

## Removed Rules

| Rule Code | Reason | Impact |
|---|---|---|
| <rule-code> | <reason> | <impact> |

## Impact Summary

- <impact-summary>

## Files

- `.local/harness/<date>/<run-id>/diff-from-previous.md`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : warning
SUMMARY  : Replace template placeholders and verify the rule diff before handoff.
ARTIFACTS: .local/harness/<date>/<run-id>/diff-from-previous.md
NEXT     : Fill the report, confirm impact summary, then hand off with the monthly rule pack.
======================
```
