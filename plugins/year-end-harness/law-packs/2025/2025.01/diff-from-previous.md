# Rule Diff

## Context

- As-of date: 2026-04-16
- Run id: 20260415-152140-personal-deduction+20260415-160427-pension-insurance-premium
- Run directory: .local/harness/2026-04-15/20260415-152140-personal-deduction
- Target law context: 2025 income / 2026 filing
- Current rule version: 2025.01
- Previous rule version: first version

## Inputs

- `.local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack.json`
- `.local/harness/2026-04-15/20260415-160427-pension-insurance-premium/source-manifest.json`
- `.local/harness/2026-04-15/20260415-160427-pension-insurance-premium/normalized-rule-pack.json`
- first version because no published normalized rule pack existed under `plugins/year-end-harness/law-packs/` before 2025.01.

## Version Pair

- Current rule set id: 2025@2025.01-personal-and-pension-merged
- Previous rule set id: first version
- Change summary: first version merging personal deduction and pension insurance premium rule codes under 2025.01.

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
| PENSION_INSURANCE_PREMIUM_ELIGIBILITY_2025 | PENSION_INSURANCE_PREMIUM | eligiblePaidAmountSource=employeePublicPensionContributionAmount; excludeEmployerContribution=True | law-income-tax-act-51-3-2025-10-01 |
| PENSION_INSURANCE_PREMIUM_PAID_AMOUNT_DEDUCTION_2025 | PENSION_INSURANCE_PREMIUM | formula=eligiblePaidAmount; floorAtZero=True | law-income-tax-act-51-3-2025-10-01 |
| PENSION_INSURANCE_PREMIUM_AGGREGATE_INCOME_CAP_2025 | PENSION_INSURANCE_PREMIUM | capAmountSource=comprehensiveIncomeAmount; includedDeductionFamilies=[PERSONAL_DEDUCTION, PENSION_INSURANCE_PREMIUM, REVERSE_MORTGAGE_INTEREST, SPECIAL_INCOME_DEDUCTION, TAX_SPECIAL_LAW_INCOME_DEDUCTION] | law-income-tax-act-51-3-2025-10-01 |
| PENSION_INSURANCE_PREMIUM_TRACE_FORMULA_2025 | PENSION_INSURANCE_PREMIUM | traceFields=[eligiblePaidAmount, appliedAmount, aggregateCapRemainingAmount] | law-income-tax-act-51-3-2025-10-01 |

## Changed Rules

| Rule Code | Change Type | Impact | Source Refs |
|---|---|---|---|
| None | first version | No prior published rule to compare. | None |

## Removed Rules

| Rule Code | Reason | Impact |
|---|---|---|
| None | first version | No prior published rule to remove. |

## Impact Summary

- This is the first published version of the 2025.01 normalized rule pack for this repository.
- It covers personal deduction (Articles 50 and 51) and pension insurance premium deduction (Article 51-3).
- Runtime implementation for pension insurance premium is unblocked only after this merge; personal deduction implementation was already landed in commit cd90e4d.

## Files

- `plugins/year-end-harness/law-packs/2025/2025.01/diff-from-previous.md`
- `plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : success
SUMMARY  : First-version 2025.01 rule diff documented personal-deduction and pension-insurance-premium merge.
ARTIFACTS: .local/harness/2026-04-15/20260415-152140-personal-deduction/diff-from-previous.md
NEXT     : Continue pension-insurance-premium implementation against the published rule codes.
======================
```
