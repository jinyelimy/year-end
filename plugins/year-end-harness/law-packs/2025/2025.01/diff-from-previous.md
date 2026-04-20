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
| VENTURE_INVESTMENT_RATE_DIRECT_TIERED_2025 | VENTURE_INVESTMENT | tiers=[{max:30M,rate:1.00},{max:50M,rate:0.70},{max:null,rate:0.30}] (직접투자 구간별 공제율) | law-stra-16-venture-investment-2025-01-01 |
| VENTURE_INVESTMENT_RATE_FUND_2025 | VENTURE_INVESTMENT | deductionRate=0.10 (조합/전문회사/투자신탁 출자 10% 일률) | law-stra-16-venture-investment-2025-01-01 |
| VENTURE_INVESTMENT_INCOME_LIMIT_2025 | VENTURE_INVESTMENT | comprehensiveIncomeLimitRate=0.50 (종합소득금액의 50% 한도) | law-stra-16-venture-investment-2025-01-01 |
| VENTURE_INVESTMENT_TRACE_2025 | VENTURE_INVESTMENT | traceFields=[ventureDirectInvestmentAmount, ventureFundInvestmentAmount, ventureDirectDeductionAmount, ventureFundDeductionAmount, ventureDeductionBeforeIncomeCapAmount, ventureInvestmentDeductionAmount] | law-stra-16-venture-investment-2025-01-01 |
| EMPLOYEE_STOCK_OWNERSHIP_RATE_2025 | EMPLOYEE_STOCK_OWNERSHIP | deductionRate=1.00 (출연금 100% 공제) | law-stra-88-4-employee-stock-ownership-2025-01-01 |
| EMPLOYEE_STOCK_OWNERSHIP_LIMIT_2025 | EMPLOYEE_STOCK_OWNERSHIP | regularAnnualDeductionLimit=4000000 (일반 기업 연 400만원); ventureAnnualDeductionLimit=15000000 (벤처기업 연 1,500만원) | law-stra-88-4-employee-stock-ownership-2025-01-01 |
| EMPLOYEE_STOCK_OWNERSHIP_TRACE_2025 | EMPLOYEE_STOCK_OWNERSHIP | traceFields=[employeeStockOwnershipContributionAmount, employeeStockOwnershipAppliedLimitAmount, employeeStockOwnershipDeductionAmount] | law-stra-88-4-employee-stock-ownership-2025-01-01 |
| FOREIGN_TAX_CREDIT_RATE_2025 | FOREIGN_TAX_CREDIT | creditRate=1.00 (외국납부세액 전액 세액공제) | law-income-tax-act-57-foreign-tax-credit-2025-01-01 |
| FOREIGN_TAX_CREDIT_LIMIT_FORMULA_2025 | FOREIGN_TAX_CREDIT | formula=domesticCalculatedTax * (foreignSourceIncomeAmount / comprehensiveIncomeAmount); zeroDivisionPolicy=return_zero_when_denominator_zero | law-income-tax-act-57-foreign-tax-credit-2025-01-01 |
| FOREIGN_TAX_CREDIT_TRACE_2025 | FOREIGN_TAX_CREDIT | traceFields=[foreignTaxPaidAmount, foreignSourceIncomeAmount, comprehensiveIncomeAmount, domesticCalculatedTaxAmount, foreignTaxCreditLimitAmount, foreignTaxCreditAmount] | law-income-tax-act-57-foreign-tax-credit-2025-01-01 |
| TAX_PAYER_ASSOCIATION_CREDIT_RATE_2025 | TAX_PAYER_ASSOCIATION_CREDIT | creditRate=0.05 (납세조합 징수 근로소득 산출세액의 5% 세액공제) | law-income-tax-act-150-tax-payer-association-2025-01-01 |
| TAX_PAYER_ASSOCIATION_CREDIT_FORMULA_2025 | TAX_PAYER_ASSOCIATION_CREDIT | formula=domesticCalculatedTax * (taxPayerAssociationIncomeAmount / earnedIncomeAmount) * creditRate; zeroDivisionPolicy=return_zero_when_denominator_zero; eligibleIncomeType=EARNED_INCOME | law-income-tax-act-150-tax-payer-association-2025-01-01 |
| TAX_PAYER_ASSOCIATION_CREDIT_TRACE_2025 | TAX_PAYER_ASSOCIATION_CREDIT | traceFields=[taxPayerAssociationIncomeAmount, earnedIncomeAmount, domesticCalculatedTaxAmount, taxPayerAssociationAllocatedTaxAmount, taxPayerAssociationCreditAmount] | law-income-tax-act-150-tax-payer-association-2025-01-01 |
| SME_YOUTH_EMPLOYEE_TAX_REDUCTION_RATE_2025 | SME_YOUTH_EMPLOYEE_TAX_REDUCTION | rateByCategory={YOUTH:0.90, SENIOR:0.70, DISABLED:0.70, CAREER_INTERRUPTED_WOMAN:0.70} (대상유형별 감면율) | law-stra-30-sme-youth-employee-tax-reduction-2025-01-01 |
| SME_YOUTH_EMPLOYEE_TAX_REDUCTION_LIMIT_2025 | SME_YOUTH_EMPLOYEE_TAX_REDUCTION | annualLimitByCategory={YOUTH:2000000, SENIOR:1500000, DISABLED:1500000, CAREER_INTERRUPTED_WOMAN:1500000} (대상유형별 연간 감면한도) | law-stra-30-sme-youth-employee-tax-reduction-2025-01-01 |
| SME_YOUTH_EMPLOYEE_TAX_REDUCTION_ELIGIBILITY_2025 | SME_YOUTH_EMPLOYEE_TAX_REDUCTION | youthMinAge=15, youthMaxAge=34, seniorMinAge=60, reductionYearsByCategory={YOUTH:5, SENIOR:3, DISABLED:3, CAREER_INTERRUPTED_WOMAN:3}, militaryServiceDeductionMaxYears=6, requiresSmeEmployer=true | law-stra-30-sme-youth-employee-tax-reduction-2025-01-01 |
| SME_YOUTH_EMPLOYEE_TAX_REDUCTION_TRACE_2025 | SME_YOUTH_EMPLOYEE_TAX_REDUCTION | traceFields=[smeYouthCategory, smeYouthEmployerIncomeAmount, smeYouthAllocatedTaxAmount, smeYouthReductionRate, smeYouthAnnualLimitAmount, smeYouthEmployeeTaxReductionAmount] | law-stra-30-sme-youth-employee-tax-reduction-2025-01-01 |

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
