# Rule Diff

## Context

- As-of date: 2026-04-16
- Run id: 20260415-152140-personal-deduction+20260415-160427-pension-insurance-premium+20260416-134405-social-insurance-special+20260416-155000-credit-card
- Run directory: .local/harness/2026-04-16/20260416-155000-credit-card
- Target law context: 2025 income / 2026 filing
- Current rule version: 2025.01
- Previous rule version: first version

## Inputs

- `.local/harness/2026-04-15/20260415-152140-personal-deduction/source-manifest.json`
- `.local/harness/2026-04-15/20260415-152140-personal-deduction/normalized-rule-pack.json`
- `.local/harness/2026-04-15/20260415-160427-pension-insurance-premium/source-manifest.json`
- `.local/harness/2026-04-15/20260415-160427-pension-insurance-premium/normalized-rule-pack.json`
- `.local/harness/2026-04-16/20260416-134405-social-insurance-special/source-manifest.json`
- `.local/harness/2026-04-16/20260416-134405-social-insurance-special/normalized-rule-pack.json`
- first version because no published normalized rule pack existed under `plugins/year-end-harness/law-packs/` before 2025.01.

## Version Pair

- Current rule set id: 2025@2025.01-personal-pension-social-creditcard-merged
- Previous rule set id: first version
- Change summary: first version merging personal deduction, pension insurance premium, social insurance premium, and credit card deduction rule codes under 2025.01.

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
| SOCIAL_INSURANCE_PREMIUM_HEALTH_ELIGIBILITY_2025 | SOCIAL_INSURANCE_PREMIUM | eligiblePaidAmountSource=employeeHealthInsurancePremiumAmount; includesLongTermCare=True; excludeEmployerContribution=True | law-income-tax-act-52-2025-10-01, law-national-health-insurance-act-69-2025-10-01 |
| SOCIAL_INSURANCE_PREMIUM_EMPLOYMENT_ELIGIBILITY_2025 | SOCIAL_INSURANCE_PREMIUM | eligiblePaidAmountSource=employeeEmploymentInsurancePremiumAmount; excludeEmployerContribution=True | law-income-tax-act-52-2025-10-01, law-employment-insurance-act-49-2025-10-01 |
| SOCIAL_INSURANCE_PREMIUM_AGGREGATE_CAP_2025 | SOCIAL_INSURANCE_PREMIUM | capSource=comprehensiveIncomeAmount; includedDeductionFamilies=[인적공제, 연금보험료공제] | law-income-tax-act-52-2025-10-01 |
| SOCIAL_INSURANCE_PREMIUM_TRACE_FORMULA_2025 | SOCIAL_INSURANCE_PREMIUM | traceFields=[healthInsurancePremiumAmount, employmentInsurancePremiumAmount, totalEligibleAmount, aggregateCapRemainingAmount, appliedAmount] | law-income-tax-act-52-2025-10-01 |
| CREDIT_CARD_MINIMUM_USAGE_THRESHOLD_2025 | CREDIT_CARD | thresholdRate=0.25 | law-income-tax-act-126-2-2025-10-01 |
| CREDIT_CARD_RATE_CREDIT_2025 | CREDIT_CARD | subType=CREDIT_CARD; rate=0.15 | law-income-tax-act-126-2-2025-10-01 |
| CREDIT_CARD_RATE_DEBIT_CASH_ZEROPAY_2025 | CREDIT_CARD | subTypes=[DEBIT_CARD,CASH_RECEIPT,ZERO_PAY]; rate=0.30 | law-income-tax-act-126-2-2025-10-01 |
| CREDIT_CARD_BASIC_LIMIT_2025 | CREDIT_CARD | tiers=[{max:70M,limit:3M},{max:120M,limit:2.5M},{max:null,limit:2M}] | law-income-tax-act-126-2-2025-10-01 |
| CREDIT_CARD_TRACE_FORMULA_2025 | CREDIT_CARD | traceFields=[creditCardAmount,...,appliedAmount] | law-income-tax-act-126-2-2025-10-01 |
| STANDARD_TAX_CREDIT_FLAT_AMOUNT_2025 | STANDARD_TAX_CREDIT | amountPerPerson=130000 (근로자 연 13만원) | law-income-tax-act-59-4-9-2025 |
| STANDARD_TAX_CREDIT_WORKER_ELIGIBILITY_2025 | STANDARD_TAX_CREDIT | requiresEarnedIncome=True | law-income-tax-act-59-4-9-2025 |
| STANDARD_TAX_CREDIT_VS_SPECIAL_CHOICE_FORMULA_2025 | STANDARD_TAX_CREDIT | method=max; competingSubTypes=[INSURANCE_PREMIUM,MEDICAL_EXPENSE,EDUCATION_EXPENSE,DONATION]; tieBreakPreference=STANDARD | law-income-tax-act-59-4-9-2025 |
| LONG_TERM_COLLECTIVE_INVESTMENT_RATE_2025 | LONG_TERM_COLLECTIVE_INVESTMENT | deductionRate=0.40 | law-stra-91-16-long-term-collective-investment-2025-01-01 |
| LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_2025 | LONG_TERM_COLLECTIVE_INVESTMENT | annualDeductionLimit=2400000; maxAnnualContributionAmount=6000000; maxGrossSalaryAtEnrollmentAmount=50000000; contractEnrollmentDeadline=2025-12-31; minimumContractYears=10 | law-stra-91-16-long-term-collective-investment-2025-01-01 |
| LONG_TERM_COLLECTIVE_INVESTMENT_TRACE_2025 | LONG_TERM_COLLECTIVE_INVESTMENT | traceFields=[longTermCollectiveInvestmentContributionAmount, longTermCollectiveInvestmentDeductionBeforeLimitAmount, longTermCollectiveInvestmentDeductionAmount] | law-stra-91-16-long-term-collective-investment-2025-01-01 |
| YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_RATE_2025 | YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT | deductionRate=0.40 | law-stra-91-20-youth-long-term-collective-investment-2025-01-01 |
| YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_2025 | YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT | annualDeductionLimit=2400000; maxAnnualContributionAmount=6000000; maxGrossSalaryAtEnrollmentAmount=50000000; youthMinAge=19; youthMaxAge=34; militaryServiceDeductionMaxYears=6; contractEnrollmentDeadline=2025-12-31; minimumContractYears=3; maximumContractYears=5 | law-stra-91-20-youth-long-term-collective-investment-2025-01-01 |
| YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_TRACE_2025 | YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT | traceFields=[youthLongTermCollectiveInvestmentContributionAmount, youthLongTermCollectiveInvestmentDeductionBeforeLimitAmount, youthLongTermCollectiveInvestmentDeductionAmount] | law-stra-91-20-youth-long-term-collective-investment-2025-01-01 |
| SME_MUTUAL_AID_RATE_2025 | SME_MUTUAL_AID | deductionRate=1.00 (납입액 100% 공제) | law-stra-86-3-sme-mutual-aid-2025-01-01 |
| SME_MUTUAL_AID_TIERED_LIMIT_2025 | SME_MUTUAL_AID | tiers=[{max:40M,limit:5M},{max:100M,limit:3M},{max:null,limit:2M}]; eligibleRole=SME_REPRESENTATIVE | law-stra-86-3-sme-mutual-aid-2025-01-01 |
| SME_MUTUAL_AID_TRACE_2025 | SME_MUTUAL_AID | traceFields=[smeMutualAidContributionAmount, smeMutualAidIncomeBasisAmount, smeMutualAidAppliedTierLimit, smeMutualAidDeductionAmount] | law-stra-86-3-sme-mutual-aid-2025-01-01 |

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
- It covers personal deduction (Articles 50 and 51), pension insurance premium deduction (Article 51-3), social insurance premium special income deduction (Article 52), and credit card usage income deduction (Article 126-2).
- Runtime implementation for credit card deduction is unblocked only after this merge; personal deduction, pension insurance premium, and social insurance premium implementations were already landed in prior commits.

## Files

- `plugins/year-end-harness/law-packs/2025/2025.01/diff-from-previous.md`
- `plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json`

## Validation

```text
=== HARNESS RESULT ===
STATUS   : success
SUMMARY  : First-version 2025.01 rule diff documents personal-deduction, pension-insurance-premium, social-insurance-premium, and credit-card merge.
ARTIFACTS: .local/harness/2026-04-16/20260416-155000-credit-card/diff-from-previous.md
NEXT     : Continue credit-card slice implementation against the published rule codes.
======================
```
