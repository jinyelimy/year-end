package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.EarnedIncomeDeductionCalculator.EarnedIncomeDeductionRuleSnapshot;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.infrastructure.InsurancePremiumDeductionPolicy;
import com.example.yearend.calculation.domain.PensionAccountTaxCreditCalculator;
import com.example.yearend.calculation.domain.MonthlyRentTaxCreditCalculator;
import com.example.yearend.calculation.domain.HousingLoanDeductionCalculator;
import com.example.yearend.calculation.domain.LongTermMortgageDeductionCalculator;
import com.example.yearend.calculation.domain.HousingSavingsDeductionCalculator;
import com.example.yearend.calculation.domain.LongTermCollectiveInvestmentDeductionCalculator;
import com.example.yearend.calculation.domain.YouthLongTermCollectiveInvestmentDeductionCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EarnedIncomeDeductionCalculatorTest {

    private final EarnedIncomeDeductionCalculator calculator = new EarnedIncomeDeductionCalculator(new ObjectMapper());

    @Test
    @DisplayName("calculates bracket boundaries from the resolved rule snapshot")
    void calculatesBracketBoundaries() {
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());

        assertThat(ruleSnapshot.bracketsEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(ruleSnapshot.maxLimitEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(calculator.calculate(0L, ruleSnapshot)).isZero();
        assertThat(calculator.calculate(5_000_000L, ruleSnapshot)).isEqualTo(3_500_000L);
        assertThat(calculator.calculate(15_000_000L, ruleSnapshot)).isEqualTo(7_500_000L);
        assertThat(calculator.calculate(45_000_000L, ruleSnapshot)).isEqualTo(12_000_000L);
        assertThat(calculator.calculate(100_000_000L, ruleSnapshot)).isEqualTo(14_750_000L);
    }

    @Test
    @DisplayName("applies the maximum limit from the resolved rule snapshot")
    void appliesMaximumLimit() {
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());

        assertThat(calculator.calculate(400_000_000L, ruleSnapshot)).isEqualTo(20_000_000L);
    }

    @Test
    @DisplayName("uses changed rule parameters without code changes")
    void usesChangedRuleParameters() {
        RuleSetSnapshot customSnapshot = new RuleSetSnapshot(
            "custom-rule-set",
            "2025.99",
            "custom-hash",
            true,
            List.of(
                deductionRule(
                    EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {"brackets":[{"fromInclusive":0,"toInclusive":null,"baseDeductionAmount":0,"excessBaseAmount":0,"excessRatePercent":10}]}
                        """,
                    LocalDate.of(2025, 4, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeDeductionCalculator.MAX_LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":100}",
                    LocalDate.of(2025, 4, 1),
                    null
                )
            )
        );
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(customSnapshot);

        assertThat(calculator.calculate(10_000L, ruleSnapshot)).isEqualTo(100L);
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain earned income deduction rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE);
    }

    static RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@2025.01",
            "2025.01",
            "official-hash",
            true,
            List.of(
                deductionRule(
                    EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {
                          "brackets": [
                            {"fromInclusive":0,"toInclusive":5000000,"baseDeductionAmount":0,"excessBaseAmount":0,"excessRatePercent":70},
                            {"fromInclusive":5000001,"toInclusive":15000000,"baseDeductionAmount":3500000,"excessBaseAmount":5000000,"excessRatePercent":40},
                            {"fromInclusive":15000001,"toInclusive":45000000,"baseDeductionAmount":7500000,"excessBaseAmount":15000000,"excessRatePercent":15},
                            {"fromInclusive":45000001,"toInclusive":100000000,"baseDeductionAmount":12000000,"excessBaseAmount":45000000,"excessRatePercent":5},
                            {"fromInclusive":100000001,"toInclusive":null,"baseDeductionAmount":14750000,"excessBaseAmount":100000000,"excessRatePercent":2}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeDeductionCalculator.MAX_LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":20000000}",
                    LocalDate.of(2025, 1, 1),
                    null
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.BASIC_AMOUNT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amountPerPerson\":1500000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.BASIC_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "incomeLimitAmount": 1000000,
                          "salaryOnlyGrossLimitAmount": 5000000,
                          "parentMinAge": 60,
                          "childMaxAge": 20,
                          "disabledDependentsIgnoreAgeLimit": true,
                          "supportedRelations": ["SELF", "SPOUSE", "CHILD", "PARENT"]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.SENIOR_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amountPerPerson\":1000000,\"minAge\":70,\"requiresBasicDeductionTarget\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.DISABLED_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amountPerPerson\":2000000,\"requiresBasicDeductionTarget\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.WOMAN_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amount\":500000,\"maxComprehensiveIncomeAmount\":30000000,\"requiresExplicitFilerFlag\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.SINGLE_PARENT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amount\":1000000,\"requiresNoSpouse\":true,\"requiresBasicDeductionTargetChild\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PERSONAL_DEDUCTION,
                    PersonalDeductionCalculator.AGGREGATION_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "formula": "basicAmount + seniorAmount + disabledAmount + max(singleParentAmount, womanAmount)",
                          "singleParentOverridesWoman": true,
                          "floorAtZero": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_INSURANCE_PREMIUM,
                    PensionInsurancePremiumCalculator.ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "eligiblePaidAmountSource": "employeePublicPensionContributionAmount",
                          "excludeEmployerContribution": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_INSURANCE_PREMIUM,
                    PensionInsurancePremiumCalculator.PAID_AMOUNT_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "formula": "eligiblePaidAmount",
                          "floorAtZero": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_INSURANCE_PREMIUM,
                    PensionInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE,
                    RuleCategory.LIMIT,
                    """
                        {
                          "capAmountSource": "comprehensiveIncomeAmount",
                          "includedDeductionFamilies": [
                            "PERSONAL_DEDUCTION",
                            "PENSION_INSURANCE_PREMIUM",
                            "REVERSE_MORTGAGE_INTEREST",
                            "SPECIAL_INCOME_DEDUCTION",
                            "TAX_SPECIAL_LAW_INCOME_DEDUCTION"
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_INSURANCE_PREMIUM,
                    PensionInsurancePremiumCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "traceFields": [
                            "eligiblePaidAmount",
                            "appliedAmount",
                            "aggregateCapRemainingAmount"
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.SOCIAL_INSURANCE_PREMIUM,
                    SocialInsurancePremiumCalculator.HEALTH_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "eligiblePaidAmountSource": "employeeHealthInsurancePremiumAmount",
                          "includesLongTermCare": true,
                          "excludeEmployerContribution": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.SOCIAL_INSURANCE_PREMIUM,
                    SocialInsurancePremiumCalculator.EMPLOYMENT_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "eligiblePaidAmountSource": "employeeEmploymentInsurancePremiumAmount",
                          "excludeEmployerContribution": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.SOCIAL_INSURANCE_PREMIUM,
                    SocialInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE,
                    RuleCategory.LIMIT,
                    """
                        {
                          "capSource": "comprehensiveIncomeAmount",
                          "includedDeductionFamilies": ["인적공제", "연금보험료공제"]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.SOCIAL_INSURANCE_PREMIUM,
                    SocialInsurancePremiumCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "traceFields": [
                            "healthInsurancePremiumAmount",
                            "employmentInsurancePremiumAmount",
                            "totalEligibleAmount",
                            "aggregateCapRemainingAmount",
                            "appliedAmount"
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INCOME_TAX,
                    IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {
                          "calculationMethod": "TAX_BASE_TIMES_RATE_MINUS_QUICK_DEDUCTION",
                          "brackets": [
                            {"sequence":1,"fromExclusive":null,"upToInclusive":14000000,"rate":0.06,"quickDeductionAmount":0},
                            {"sequence":2,"fromExclusive":14000000,"upToInclusive":50000000,"rate":0.15,"quickDeductionAmount":1260000},
                            {"sequence":3,"fromExclusive":50000000,"upToInclusive":88000000,"rate":0.24,"quickDeductionAmount":5760000},
                            {"sequence":4,"fromExclusive":88000000,"upToInclusive":150000000,"rate":0.35,"quickDeductionAmount":15440000},
                            {"sequence":5,"fromExclusive":150000000,"upToInclusive":300000000,"rate":0.38,"quickDeductionAmount":19940000},
                            {"sequence":6,"fromExclusive":300000000,"upToInclusive":500000000,"rate":0.40,"quickDeductionAmount":25940000},
                            {"sequence":7,"fromExclusive":500000000,"upToInclusive":1000000000,"rate":0.42,"quickDeductionAmount":35940000},
                            {"sequence":8,"fromExclusive":1000000000,"upToInclusive":null,"rate":0.45,"quickDeductionAmount":65940000}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INCOME_TAX,
                    EarnedIncomeTaxCreditCalculator.BASE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "taxAmountSource": "calculatedTaxAmount",
                          "brackets": [
                            {"sequence":1,"upToInclusive":1300000,"baseCreditAmount":0,"excessBaseAmount":0,"excessCreditRate":0.55},
                            {"sequence":2,"fromExclusive":1300000,"upToInclusive":null,"baseCreditAmount":715000,"excessBaseAmount":1300000,"excessCreditRate":0.30}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INCOME_TAX,
                    EarnedIncomeTaxCreditCalculator.LIMIT_BY_GROSS_SALARY_RULE_CODE,
                    RuleCategory.LIMIT,
                    """
                        {
                          "salaryAmountSource": "totalGrossSalaryAmount",
                          "limitBrackets": [
                            {"sequence":1,"upToInclusive":33000000,"limitAmount":740000},
                            {"sequence":2,"fromExclusive":33000000,"upToInclusive":70000000,"baseLimitAmount":740000,"reductionBaseAmount":33000000,"reductionRate":0.008,"minimumLimitAmount":660000},
                            {"sequence":3,"fromExclusive":70000000,"upToInclusive":120000000,"baseLimitAmount":660000,"reductionBaseAmount":70000000,"reductionRate":0.5,"minimumLimitAmount":500000},
                            {"sequence":4,"fromExclusive":120000000,"upToInclusive":null,"baseLimitAmount":500000,"reductionBaseAmount":120000000,"reductionRate":0.5,"minimumLimitAmount":200000}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CREDIT_CARD,
                    CreditCardDeductionCalculator.MINIMUM_USAGE_THRESHOLD_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"thresholdRate\":0.25}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CREDIT_CARD,
                    CreditCardDeductionCalculator.RATE_CREDIT_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"rate\":0.15}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CREDIT_CARD,
                    CreditCardDeductionCalculator.RATE_DEBIT_CASH_ZEROPAY_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"rate\":0.30}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CREDIT_CARD,
                    CreditCardDeductionCalculator.BASIC_LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"tiers\":[{\"maxSalary\":70000000,\"limit\":3000000},{\"maxSalary\":120000000,\"limit\":2500000},{\"maxSalary\":null,\"limit\":2000000}]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CREDIT_CARD,
                    CreditCardDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"totalUsageAmount\",\"minimumUsageThresholdAmount\",\"creditCardAboveThresholdAmount\",\"debitCashZeroPayAboveThresholdAmount\",\"deductionBeforeLimitAmount\",\"basicLimitAmount\",\"appliedAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INCOME_TAX,
                    EarnedIncomeTaxCreditCalculator.FINAL_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "formula": "min(baseCreditAmount, salaryBasedLimitAmount)",
                          "baseRuleCode": "EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025",
                          "limitRuleCode": "EARNED_INCOME_TAX_CREDIT_LIMIT_BY_GROSS_SALARY_2025",
                          "floorAtZero": true
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.POLITICAL_BRACKET_RULE_CODE,
                    RuleCategory.BRACKET,
                    "{\"firstThreshold\":100000,\"firstRate\":0.9091,\"secondThreshold\":30000000,\"secondRate\":0.15,\"thirdRate\":0.25}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.LEGAL_LIMIT_RATE_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"incomeLimitRate\":1.0,\"creditRateBelowThreshold\":0.15,\"creditRateAboveThreshold\":0.30,\"creditThresholdAmount\":10000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.DESIGNATED_RELIGIOUS_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"incomeLimitRate\":0.10,\"creditRateBelowThreshold\":0.15,\"creditRateAboveThreshold\":0.30,\"creditThresholdAmount\":10000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.DESIGNATED_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"incomeLimitRate\":0.30,\"creditRateBelowThreshold\":0.15,\"creditRateAboveThreshold\":0.30,\"creditThresholdAmount\":10000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.EMPLOYEE_STOCK_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"incomeLimitRate\":0.30,\"creditRate\":0.30}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.AGGREGATE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"calculationOrder\":[\"POLITICAL\",\"LEGAL\",\"EMPLOYEE_STOCK\",\"DESIGNATED\",\"DESIGNATED_RELIGIOUS\"],\"incomeBasisField\":\"earnedIncomeAmount\",\"carryForwardDeferred\":true,\"floorAtZero\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.DONATION,
                    DonationTaxCreditCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"politicalDonationAmount\",\"legalDonationAmount\",\"employeeStockDonationAmount\",\"designatedDonationAmount\",\"designatedReligiousDonationAmount\",\"totalDonationTaxCreditAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INSURANCE,
                    InsurancePremiumDeductionPolicy.STANDARD_LIMIT_RATE_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":1000000,\"creditRate\":0.12,\"maxCredit\":120000,\"subType\":\"INSURANCE_PREMIUM\"}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INSURANCE,
                    InsurancePremiumDeductionPolicy.DISABILITY_LIMIT_RATE_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":1000000,\"creditRate\":0.15,\"maxCredit\":150000,\"subType\":\"INSURANCE_PREMIUM_DISABILITY\"}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INSURANCE,
                    InsurancePremiumDeductionPolicy.AGGREGATE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"formula\":\"standardCredit + disabilityCredit\",\"subtypes\":[\"INSURANCE_PREMIUM\",\"INSURANCE_PREMIUM_DISABILITY\"],\"independentLimitsPerSubtype\":true,\"floorAtZero\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.INSURANCE,
                    InsurancePremiumDeductionPolicy.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"subType\",\"amount\",\"limitAmount\",\"creditRate\",\"taxCreditContribution\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CHILD_TAX_CREDIT,
                    ChildTaxCreditCalculator.AMOUNT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"minAge\":8,\"firstChildAmount\":150000,\"secondChildAmount\":300000,\"additionalChildAmount\":300000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CHILD_TAX_CREDIT,
                    ChildTaxCreditCalculator.BIRTH_ADOPTION_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"firstChildAmount\":300000,\"secondChildAmount\":500000,\"thirdOrMoreChildAmount\":700000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.CHILD_TAX_CREDIT,
                    ChildTaxCreditCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"eligibleChildCount\",\"basicChildCreditAmount\",\"birthAdoptionChildCount\",\"birthAdoptionCreditAmount\",\"childTaxCreditAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_ACCOUNT,
                    PensionAccountTaxCreditCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"pensionSavingsLimit\":6000000,\"combinedPensionLimit\":9000000,\"isaAdditionalRate\":0.10,\"isaAdditionalMaxLimit\":3000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_ACCOUNT,
                    PensionAccountTaxCreditCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"grossSalaryThreshold\":55000000,\"highRate\":0.15,\"lowRate\":0.12}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.MONTHLY_RENT,
                    MonthlyRentTaxCreditCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"annualRentLimit\":12000000,\"grossSalaryEligibilityLimit\":80000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.MONTHLY_RENT,
                    MonthlyRentTaxCreditCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"grossSalaryThreshold\":55000000,\"highRate\":0.17,\"lowRate\":0.15}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.MONTHLY_RENT,
                    MonthlyRentTaxCreditCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"annualRentAmount\",\"eligibleRentAmount\",\"creditRate\",\"monthlyRentTaxCreditAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.PENSION_ACCOUNT,
                    PensionAccountTaxCreditCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"pensionSavingsAmount\",\"irpAmount\",\"applicablePensionSavingsAmount\",\"applicableCombinedAmount\",\"isaMaturityTransferAmount\",\"isaAdditionalAmount\",\"totalApplicableAmount\",\"creditRate\",\"pensionAccountTaxCreditAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_LOAN,
                    HousingLoanDeductionCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":4000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_LOAN,
                    HousingLoanDeductionCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_LOAN,
                    HousingLoanDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"housingLoanBankRepaymentAmount\",\"housingLoanIndividualRepaymentAmount\",\"totalRepaymentAmount\",\"deductionBeforeLimitAmount\",\"housingLoanDeductionAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.LONG_TERM_MORTGAGE,
                    LongTermMortgageDeductionCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"tiers\":[{\"repaymentType\":\"NON_INSTALLMENT_FIXED\",\"limitAmount\":20000000},{\"repaymentType\":\"NON_INSTALLMENT_OR_FIXED\",\"limitAmount\":18000000},{\"repaymentType\":\"OTHER_15Y\",\"limitAmount\":8000000},{\"repaymentType\":\"UNDER_15Y\",\"limitAmount\":6000000}]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.LONG_TERM_MORTGAGE,
                    LongTermMortgageDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"longTermMortgageInterestAmount\",\"longTermMortgageRepaymentType\",\"longTermMortgageLimitAmount\",\"longTermMortgageDeductionAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_SAVINGS,
                    HousingSavingsDeductionCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":3000000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_SAVINGS,
                    HousingSavingsDeductionCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.HOUSING_SAVINGS,
                    HousingSavingsDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"housingSavingsContributionAmount\",\"deductionBeforeLimitAmount\",\"housingSavingsDeductionAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT,
                    LongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":2400000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT,
                    LongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT,
                    LongTermCollectiveInvestmentDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"longTermCollectiveInvestmentContributionAmount\",\"longTermCollectiveInvestmentDeductionBeforeLimitAmount\",\"longTermCollectiveInvestmentDeductionAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT,
                    YouthLongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":2400000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT,
                    YouthLongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT,
                    YouthLongTermCollectiveInvestmentDeductionCalculator.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"youthLongTermCollectiveInvestmentContributionAmount\",\"youthLongTermCollectiveInvestmentDeductionBeforeLimitAmount\",\"youthLongTermCollectiveInvestmentDeductionAmount\"]}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.STANDARD_TAX_CREDIT,
                    StandardTaxCreditCalculator.FLAT_AMOUNT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amountPerPerson\":130000}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.STANDARD_TAX_CREDIT,
                    StandardTaxCreditCalculator.WORKER_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    "{\"requiresEarnedIncome\":true}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                ),
                deductionRule(
                    DeductionType.STANDARD_TAX_CREDIT,
                    StandardTaxCreditCalculator.CHOICE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"method\":\"max\",\"competingSubTypes\":[\"INSURANCE_PREMIUM\",\"MEDICAL_EXPENSE\",\"EDUCATION_EXPENSE\",\"DONATION\"],\"tieBreakPreference\":\"STANDARD\"}",
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                )
            )
        );
    }

    private static DeductionRule deductionRule(
        String ruleCode,
        RuleCategory ruleCategory,
        String parameterJsonb,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        return deductionRule(
            DeductionType.EARNED_INCOME_DEDUCTION,
            ruleCode,
            ruleCategory,
            parameterJsonb,
            effectiveFrom,
            effectiveTo
        );
    }

    private static DeductionRule deductionRule(
        DeductionType deductionType,
        String ruleCode,
        RuleCategory ruleCategory,
        String parameterJsonb,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(deductionType);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parameterJsonb);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setActive(true);
        return rule;
    }
}
