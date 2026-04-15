# Rule Diff

## Context

- As-of date: 2026-04-15
- Run id: 20260415-152140-personal-deduction
- Run directory: .local/harness/2026-04-15/20260415-152140-personal-deduction
- Target law context: 2025 income / 2026 filing
- Current rule version: 2025.01
- Previous rule version: first version

## Inputs

- `.local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack.json`
- first version because no published normalized rule pack exists under `plugins/year-end-harness/law-packs/`

## Version Pair

- Current rule set id: 2025@2025.01-personal-deduction-proposal
- Previous rule set id: first version
- Change summary: first version of personal deduction rules for the automated Phase 1 re-entry.

## Added Rules

| Rule Code | Deduction Type | Summary | Source Refs |
|---|---|---|---|
| PERSONAL_BASIC_DEDUCTION_AMOUNT_2025 | PERSONAL_DEDUCTION | amountPerPerson=1500000 | law-income-tax-act-50-2025-10-01 |
| PERSONAL_BASIC_ELIGIBILITY_2025 | PERSONAL_DEDUCTION | incomeLimitAmount=1000000; salaryOnlyGrossLimitAmount=5000000; parentMinAge=60; childMaxAge=20; disabledDependentsIgnoreAgeLimit=True; supportedRelations=[SELF, SPOUSE, CHILD, PARENT] | law-income-tax-act-50-2025-10-01 |
| PERSONAL_ADDITIONAL_SENIOR_2025 | PERSONAL_DEDUCTION | amountPerPerson=1000000; minAge=70; requiresBasicDeductionTarget=True | law-income-tax-act-51-2025-10-01 |
| PERSONAL_ADDITIONAL_DISABLED_2025 | PERSONAL_DEDUCTION | amountPerPerson=2000000; requiresBasicDeductionTarget=True | law-income-tax-act-51-2025-10-01 |
| PERSONAL_ADDITIONAL_WOMAN_2025 | PERSONAL_DEDUCTION | amount=500000; maxComprehensiveIncomeAmount=30000000; requiresExplicitFilerFlag=True | law-income-tax-act-51-2025-10-01 |
| PERSONAL_ADDITIONAL_SINGLE_PARENT_2025 | PERSONAL_DEDUCTION | amount=1000000; requiresNoSpouse=True; requiresBasicDeductionTargetChild=True | law-income-tax-act-51-2025-10-01 |
| PERSONAL_DEDUCTION_AGGREGATION_FORMULA_2025 | PERSONAL_DEDUCTION | formula=basicAmount + seniorAmount + disabledAmount + max(singleParentAmount, womanAmount); singleParentOverridesWoman=True; floorAtZero=True | law-income-tax-act-50-2025-10-01, law-income-tax-act-51-2025-10-01 |

## Changed Rules

| Rule Code | Change Type | Impact | Source Refs |
|---|---|---|---|
| None | first version | No prior published rule to compare. | None |

## Removed Rules

| Rule Code | Reason | Impact |
|---|---|---|
| None | first version | No prior published rule to remove. |

## Impact Summary

- This is the first version of the personal deduction normalized rule pack for this repository.
- Runtime implementation remains blocked until a human reviewer promotes or publishes the READY_FOR_REVIEW pack.

## Files

- `.local/harness/2026-04-15/20260415-152140-personal-deduction/diff-from-previous.md`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack.json`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : success
SUMMARY  : Automated first version rule diff prepared for human review.
ARTIFACTS: .local/harness/2026-04-15/20260415-152140-personal-deduction/diff-from-previous.md
NEXT     : Review the READY_FOR_REVIEW normalized rule pack before runtime implementation resumes.
======================
```
