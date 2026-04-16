# Agent A Tax Pack

## Context

- As-of date: 2026-04-16
- Run id: 20260415-152140-personal-deduction+20260415-160427-pension-insurance-premium+20260416-134405-social-insurance-special+20260416-155000-credit-card
- Run directory: .local/harness/2026-04-16/20260416-155000-credit-card
- Target law context: 2025 income / 2026 filing
- Target tax year: 2025
- Target filing year: 2026
- Target rule version: 2025.01
- Requested scope: Phase 1 re-entry for personal deduction, pension insurance premium, social insurance premium, and credit card deduction (all merged into 2025.01 via manual human approval).

## Inputs

- AGENTS.md
- docs/notes/command_list.md
- plugins/year-end-harness/automation/inner-workflow.md
- .local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json
- .local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack-proposal.json
- .local/harness/2026-04-15/20260415-152140-personal-deduction/expected-rule-codes.txt
- .local/harness/2026-04-15/20260415-160427-pension-insurance-premium/source-manifest.json
- .local/harness/2026-04-15/20260415-160427-pension-insurance-premium/normalized-rule-pack-proposal.json
- .local/harness/2026-04-15/20260415-160427-pension-insurance-premium/expected-rule-codes.txt
- .local/harness/2026-04-16/20260416-134405-social-insurance-special/source-manifest.json
- .local/harness/2026-04-16/20260416-134405-social-insurance-special/normalized-rule-pack-proposal.json
- .local/harness/2026-04-16/20260416-134405-social-insurance-special/expected-rule-codes.txt
- .local/harness/2026-04-16/20260416-155000-credit-card/source-manifest.json
- .local/harness/2026-04-16/20260416-155000-credit-card/normalized-rule-pack-proposal.json
- .local/harness/2026-04-16/20260416-155000-credit-card/expected-rule-codes.txt

## Source Register

| Source | URL | Published At | Effective At | Checked At | Notes |
|---|---|---|---|---|---|
| Income Tax Act Article 50 basic personal deduction | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0050&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-15 | Article 50 confirms KRW 1,500,000 basic deduction per eligible person and the spouse/dependent income and age categories. |
| Income Tax Act Article 51 additional personal deduction | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0051&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-15 | Article 51 confirms senior, disabled, woman, and single-parent additional deduction amounts and the woman/single-parent precedence rule. |
| Income Tax Act Article 51-3 pension insurance premium deduction | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=03&joNo=0051&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-15 | Article 51-3 confirms deduction for public pension-law contributions or personal burdens paid during the taxable period and the aggregate income cap rule. |
| Income Tax Act Article 52 special income deduction - social insurance premiums | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0052&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-16 | Article 52 para 1 item 1: employee health insurance + long-term care premiums deductible in full. Item 2: employee employment insurance premiums deductible in full. Para 4: aggregate cap equals comprehensive income. |
| National Health Insurance Act Article 69 | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joNo=0069&lsiSeq=276000&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-16 | Defines workplace subscriber health insurance premium; employee bears half of the total premium. |
| Employment Insurance Act Article 49 | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joNo=0049&lsiSeq=270000&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-16 | Defines employment insurance premium; employee portion is 0.9% of monthly salary for 2025. |
| 소득세법 제126조의2 신용카드 등 사용금액 소득공제 | https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joNo=0126_2&lsiSeq=276127&urlMode=lsScJoRltInfoR | 2025-10-01 | 2025-10-01 | 2026-04-16 | 총급여액의 25% 초과분 공제. 신용카드 15%, 직불카드·현금영수증·제로페이 30%. 연간 한도 구간별 300만/250만/200만 원. |
| National Tax Service year-end settlement guide | https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=238938 | 2026-04-15 | 2025-01-01 | 2026-04-15 | Official NTS year-end settlement guide index used as the administrative source registry entry for this slice. |

## Confirmed Rules

### 세율표

- None. This scope only covers personal deduction, pension insurance premium, and social insurance premium rules.

### 공제 한도표

- `PERSONAL_BASIC_DEDUCTION_AMOUNT_2025` (PERSONAL_DEDUCTION/BASIC, LIMIT): amountPerPerson=1500000; sources=law-income-tax-act-50-2025-10-01
- `PERSONAL_ADDITIONAL_SENIOR_2025` (PERSONAL_DEDUCTION/SENIOR, LIMIT): amountPerPerson=1000000; minAge=70; requiresBasicDeductionTarget=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_DISABLED_2025` (PERSONAL_DEDUCTION/DISABLED, LIMIT): amountPerPerson=2000000; requiresBasicDeductionTarget=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_WOMAN_2025` (PERSONAL_DEDUCTION/WOMAN, LIMIT): amount=500000; maxComprehensiveIncomeAmount=30000000; requiresExplicitFilerFlag=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_ADDITIONAL_SINGLE_PARENT_2025` (PERSONAL_DEDUCTION/SINGLE_PARENT, LIMIT): amount=1000000; requiresNoSpouse=True; requiresBasicDeductionTargetChild=True; sources=law-income-tax-act-51-2025-10-01
- `PERSONAL_DEDUCTION_AGGREGATION_FORMULA_2025` (PERSONAL_DEDUCTION/AGGREGATION, FORMULA): formula=basicAmount + seniorAmount + disabledAmount + max(singleParentAmount, womanAmount); singleParentOverridesWoman=True; floorAtZero=True; sources=law-income-tax-act-50-2025-10-01, law-income-tax-act-51-2025-10-01
- `PENSION_INSURANCE_PREMIUM_PAID_AMOUNT_DEDUCTION_2025` (PENSION_INSURANCE_PREMIUM/PAID_AMOUNT, FORMULA): formula=eligiblePaidAmount; floorAtZero=True; sources=law-income-tax-act-51-3-2025-10-01
- `PENSION_INSURANCE_PREMIUM_AGGREGATE_INCOME_CAP_2025` (PENSION_INSURANCE_PREMIUM/AGGREGATE_CAP, LIMIT): capAmountSource=comprehensiveIncomeAmount; includedDeductionFamilies=[PERSONAL_DEDUCTION, PENSION_INSURANCE_PREMIUM, REVERSE_MORTGAGE_INTEREST, SPECIAL_INCOME_DEDUCTION, TAX_SPECIAL_LAW_INCOME_DEDUCTION]; sources=law-income-tax-act-51-3-2025-10-01
- `PENSION_INSURANCE_PREMIUM_TRACE_FORMULA_2025` (PENSION_INSURANCE_PREMIUM/TRACE, FORMULA): traceFields=[eligiblePaidAmount, appliedAmount, aggregateCapRemainingAmount]; sources=law-income-tax-act-51-3-2025-10-01
- `SOCIAL_INSURANCE_PREMIUM_AGGREGATE_CAP_2025` (SOCIAL_INSURANCE_PREMIUM/AGGREGATE_CAP, LIMIT): capSource=comprehensiveIncomeAmount; includedDeductionFamilies=[인적공제, 연금보험료공제]; sources=law-income-tax-act-52-2025-10-01
- `SOCIAL_INSURANCE_PREMIUM_TRACE_FORMULA_2025` (SOCIAL_INSURANCE_PREMIUM/TRACE, FORMULA): traceFields=[healthInsurancePremiumAmount, employmentInsurancePremiumAmount, totalEligibleAmount, aggregateCapRemainingAmount, appliedAmount]; sources=law-income-tax-act-52-2025-10-01
- `CREDIT_CARD_BASIC_LIMIT_2025` (CREDIT_CARD/BASIC_LIMIT, LIMIT): tiers=[{maxSalary:70M,limit:3M},{maxSalary:120M,limit:2.5M},{maxSalary:null,limit:2M}]; sources=law-income-tax-act-126-2-2025-10-01

### 인적공제 판단표

- `PERSONAL_BASIC_ELIGIBILITY_2025` (PERSONAL_DEDUCTION/BASIC_ELIGIBILITY, ELIGIBILITY): incomeLimitAmount=1000000; salaryOnlyGrossLimitAmount=5000000; parentMinAge=60; childMaxAge=20; disabledDependentsIgnoreAgeLimit=True; supportedRelations=[SELF, SPOUSE, CHILD, PARENT]; sources=law-income-tax-act-50-2025-10-01
- `PENSION_INSURANCE_PREMIUM_ELIGIBILITY_2025` (PENSION_INSURANCE_PREMIUM/ELIGIBILITY, ELIGIBILITY): eligiblePaidAmountSource=employeePublicPensionContributionAmount; excludeEmployerContribution=True; sources=law-income-tax-act-51-3-2025-10-01
- `SOCIAL_INSURANCE_PREMIUM_HEALTH_ELIGIBILITY_2025` (SOCIAL_INSURANCE_PREMIUM/ELIGIBILITY, ELIGIBILITY): eligiblePaidAmountSource=employeeHealthInsurancePremiumAmount; includesLongTermCare=True; excludeEmployerContribution=True; sources=law-income-tax-act-52-2025-10-01, law-national-health-insurance-act-69-2025-10-01
- `SOCIAL_INSURANCE_PREMIUM_EMPLOYMENT_ELIGIBILITY_2025` (SOCIAL_INSURANCE_PREMIUM/ELIGIBILITY, ELIGIBILITY): eligiblePaidAmountSource=employeeEmploymentInsurancePremiumAmount; excludeEmployerContribution=True; sources=law-income-tax-act-52-2025-10-01, law-employment-insurance-act-49-2025-10-01
- `CREDIT_CARD_MINIMUM_USAGE_THRESHOLD_2025` (CREDIT_CARD/MINIMUM_USAGE_THRESHOLD, FORMULA): thresholdRate=0.25; sources=law-income-tax-act-126-2-2025-10-01
- `CREDIT_CARD_RATE_CREDIT_2025` (CREDIT_CARD/RATE_CREDIT, FORMULA): subType=CREDIT_CARD; rate=0.15; sources=law-income-tax-act-126-2-2025-10-01
- `CREDIT_CARD_RATE_DEBIT_CASH_ZEROPAY_2025` (CREDIT_CARD/RATE_DEBIT_CASH_ZEROPAY, FORMULA): subTypes=[DEBIT_CARD,CASH_RECEIPT,ZERO_PAY]; rate=0.30; sources=law-income-tax-act-126-2-2025-10-01
- `CREDIT_CARD_TRACE_FORMULA_2025` (CREDIT_CARD/TRACE, FORMULA): traceFields=[creditCardAmount,debitCardAmount,...,appliedAmount]; sources=law-income-tax-act-126-2-2025-10-01

### 항목별 가족 합산 가능 여부 표

- Basic personal deduction targets are counted per eligible person.
- Senior and disabled additional deductions require the person to be a basic deduction target.
- Single-parent additional deduction suppresses woman additional deduction when both would otherwise apply.
- Pension insurance premium deduction is a source deduction for the filer only; employer-paid contributions are excluded.

### 증빙 요구사항 표

- Eligibility and additional-deduction flags require evidence in the dependent/filer input model before runtime calculation consumes these rules.
- Runtime implementation must preserve ruleCode traceability for each counted target and amount.
- Pension insurance premium amounts must be sourced from payroll or Hometax simplified data and kept separate from protection-type insurance tax credit evidence.

## Normalization Notes

- Rule version decision: 2025.01 was selected for all slices merged in this version.
- Previous rule set: first version because `plugins/year-end-harness/law-packs/` had no published normalized rule pack before this approval.
- Diff summary: added 7 personal deduction rule codes, 4 pension insurance premium rule codes, 4 social insurance premium rule codes, and 5 credit card deduction rule codes (20 total).
- Publish readiness: `PUBLISHED` (human-approved merge).

## Inferred Rules

- None.

## Open Questions

- None. All four slices' rule codes have been confirmed against official sources and merged under 2025.01 by human approval.

## Files

- `plugins/year-end-harness/law-packs/2025/2025.01/source-manifest.json`
- `plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json`
- `plugins/year-end-harness/law-packs/2025/2025.01/diff-from-previous.md`
- `plugins/year-end-harness/law-packs/2025/2025.01/approval-manifest.json`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : success
SUMMARY  : Merged personal-deduction, pension-insurance-premium, social-insurance-premium, and credit-card rule codes under 2025.01 via human approval.
ARTIFACTS: .local/harness/2026-04-16/20260416-155000-credit-card/agent-a-tax-pack.md
NEXT     : Re-run Gate 3 and proceed with credit-card slice implementation.
======================
```
