# Agent A Tax Pack

## Context

- As-of date: 2026-04-15
- Run id: 20260415-152140-personal-deduction
- Run directory: .local/harness/2026-04-15/20260415-152140-personal-deduction
- Target law context: 2025 income / 2026 filing
- Target tax year: 2025
- Target filing year: 2026
- Target rule version: 2025.01
- Requested scope: automated Phase 1 re-entry for missing personal deduction rule codes

## Inputs

- AGENTS.md
- docs/notes/command_list.md
- plugins/year-end-harness/automation/inner-workflow.md
- .local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json
- .local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack-proposal.json
- .local/harness/2026-04-15/20260415-152140-personal-deduction/expected-rule-codes.txt

## Source Register

| Source | URL | Published At | Effective At | Checked At | Notes |
|---|---|---|---|---|---|
| Income Tax Act Article 50 basic personal deduction | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0050&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-15 | Article 50 confirms KRW 1,500,000 basic deduction per eligible person and the spouse/dependent income and age categories. |
| Income Tax Act Article 51 additional personal deduction | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0051&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-15 | Article 51 confirms senior, disabled, woman, and single-parent additional deduction amounts and the woman/single-parent precedence rule. |
| National Tax Service year-end settlement guide | https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=238938 | 2026-04-15 | 2025-01-01 | 2026-04-15 | Official NTS year-end settlement guide index used as the administrative source registry entry for this slice. |

## Confirmed Rules

### 세율표

- None. This re-entry scope only covers personal deduction rules.

### 공제 한도표

- `PERSONAL_BASIC_DEDUCTION_AMOUNT_2025` (PERSONAL_DEDUCTION/BASIC, LIMIT): amountPerPerson=1500000; sources=law-income-tax-act-50-2025-10-01
- `PERSONAL_ADDITIONAL_SENIOR_2025` (PERSONAL_DEDUCTION/SENIOR, LIMIT): amountPerPerson=1000000; minAge=70; requiresBasicDeductionTarget=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_DISABLED_2025` (PERSONAL_DEDUCTION/DISABLED, LIMIT): amountPerPerson=2000000; requiresBasicDeductionTarget=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_WOMAN_2025` (PERSONAL_DEDUCTION/WOMAN, LIMIT): amount=500000; maxComprehensiveIncomeAmount=30000000; requiresExplicitFilerFlag=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_SINGLE_PARENT_2025` (PERSONAL_DEDUCTION/SINGLE_PARENT, LIMIT): amount=1000000; requiresNoSpouse=True; requiresBasicDeductionTargetChild=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_DEDUCTION_AGGREGATION_FORMULA_2025` (PERSONAL_DEDUCTION/AGGREGATION, FORMULA): formula=basicAmount + seniorAmount + disabledAmount + max(singleParentAmount, womanAmount); singleParentOverridesWoman=True; floorAtZero=True; sources=law-income-tax-act-50-2025-10-01, law-income-tax-act-51-2025-10-01

### 인적공제 판단표

- `PERSONAL_BASIC_ELIGIBILITY_2025` (PERSONAL_DEDUCTION/BASIC_ELIGIBILITY, ELIGIBILITY): incomeLimitAmount=1000000; salaryOnlyGrossLimitAmount=5000000; parentMinAge=60; childMaxAge=20; disabledDependentsIgnoreAgeLimit=True; supportedRelations=[SELF, SPOUSE, CHILD, PARENT]; sources=law-income-tax-act-50-2025-10-01

### 항목별 가족 합산 가능 여부 표

- Basic personal deduction targets are counted per eligible person.
- Senior and disabled additional deductions require the person to be a basic deduction target.
- Single-parent additional deduction suppresses woman additional deduction when both would otherwise apply.

### 증빙 요구사항 표

- Eligibility and additional-deduction flags require evidence in the dependent/filer input model before runtime calculation consumes these rules.
- Runtime implementation must preserve ruleCode traceability for each counted target and amount.

## Normalization Notes

- Rule version decision: 2025.01 was selected from the slice proposal generated in this run.
- Previous rule set: first version because `plugins/year-end-harness/law-packs/` has no published normalized rule pack.
- Diff summary: added 7 personal deduction rule codes.
- Publish readiness: `READY_FOR_REVIEW`

## Inferred Rules

- None.

## Open Questions

- Confirm whether this READY_FOR_REVIEW pack should be promoted into `plugins/year-end-harness/law-packs/2025/2025.01/` and then published by a human reviewer.

## Files

- `.local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack.json`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/diff-from-previous.md`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/phase1-reentry-ready-for-review.md`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : success
SUMMARY  : Automated Phase 1 re-entry tax pack prepared for human review.
ARTIFACTS: .local/harness/2026-04-15/20260415-152140-personal-deduction/agent-a-tax-pack.md
NEXT     : Human reviewer may promote this READY_FOR_REVIEW pack; do not auto-PUBLISHED.
======================
```
