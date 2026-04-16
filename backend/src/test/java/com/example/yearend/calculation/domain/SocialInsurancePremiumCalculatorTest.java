package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.SocialInsurancePremiumCalculator.SocialInsurancePremiumCalculation;
import com.example.yearend.calculation.domain.SocialInsurancePremiumCalculator.SocialInsurancePremiumRuleSnapshot;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialInsurancePremiumCalculatorTest {

    private final SocialInsurancePremiumCalculator calculator = new SocialInsurancePremiumCalculator(new ObjectMapper());

    @Test
    @DisplayName("resolveRuleSnapshot resolves all four social insurance rule codes")
    void resolveRuleSnapshot_resolvesAllFourRuleCodes() {
        SocialInsurancePremiumRuleSnapshot snapshot = calculator.resolveRuleSnapshot(buildRuleSetSnapshot());

        assertThat(snapshot.healthEligibilityRule().ruleCode())
            .isEqualTo(SocialInsurancePremiumCalculator.HEALTH_ELIGIBILITY_RULE_CODE);
        assertThat(snapshot.employmentEligibilityRule().ruleCode())
            .isEqualTo(SocialInsurancePremiumCalculator.EMPLOYMENT_ELIGIBILITY_RULE_CODE);
        assertThat(snapshot.aggregateCapRule().ruleCode())
            .isEqualTo(SocialInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE);
        assertThat(snapshot.traceRule().ruleCode())
            .isEqualTo(SocialInsurancePremiumCalculator.TRACE_RULE_CODE);
    }

    @Test
    @DisplayName("calculate applies full eligible amount when under aggregate cap")
    void calculate_appliesFullAmountUnderCap() {
        SocialInsurancePremiumRuleSnapshot snapshot = calculator.resolveRuleSnapshot(buildRuleSetSnapshot());

        // comprehensiveIncome=50_000_000, priorDeductions=15_000_000 → capRemaining=35_000_000
        // health=1_200_000, employment=300_000 → totalEligible=1_500_000 < cap
        SocialInsurancePremiumCalculation calc = calculator.calculate(1_200_000L, 300_000L, 50_000_000L, 15_000_000L, snapshot);

        assertThat(calc.totalEligibleAmount()).isEqualTo(1_500_000L);
        assertThat(calc.appliedAmount()).isEqualTo(1_500_000L);
        assertThat(calc.aggregateCapRemainingAmount()).isEqualTo(35_000_000L);
    }

    @Test
    @DisplayName("calculate floors negative input at zero")
    void calculate_floorsNegativeInputAtZero() {
        SocialInsurancePremiumRuleSnapshot snapshot = calculator.resolveRuleSnapshot(buildRuleSetSnapshot());

        SocialInsurancePremiumCalculation calc = calculator.calculate(-100_000L, -50_000L, 50_000_000L, 0L, snapshot);

        assertThat(calc.effectiveHealthAmount()).isZero();
        assertThat(calc.effectiveEmploymentAmount()).isZero();
        assertThat(calc.totalEligibleAmount()).isZero();
        assertThat(calc.appliedAmount()).isZero();
    }

    @Test
    @DisplayName("calculate caps at aggregate cap remaining when eligible exceeds cap")
    void calculate_capsAtAggregateCapRemaining() {
        SocialInsurancePremiumRuleSnapshot snapshot = calculator.resolveRuleSnapshot(buildRuleSetSnapshot());

        // comprehensiveIncome=10_000_000, priorDeductions=9_500_000 → capRemaining=500_000
        // health=400_000, employment=300_000 → totalEligible=700_000 > cap
        SocialInsurancePremiumCalculation calc = calculator.calculate(400_000L, 300_000L, 10_000_000L, 9_500_000L, snapshot);

        assertThat(calc.totalEligibleAmount()).isEqualTo(700_000L);
        assertThat(calc.aggregateCapRemainingAmount()).isEqualTo(500_000L);
        assertThat(calc.appliedAmount()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("calculate produces zero applied when prior deductions exhaust comprehensive income")
    void calculate_producesZeroWhenCapExhausted() {
        SocialInsurancePremiumRuleSnapshot snapshot = calculator.resolveRuleSnapshot(buildRuleSetSnapshot());

        // comprehensiveIncome=10_000_000, priorDeductions=12_000_000 → capRemaining=0
        SocialInsurancePremiumCalculation calc = calculator.calculate(800_000L, 200_000L, 10_000_000L, 12_000_000L, snapshot);

        assertThat(calc.aggregateCapRemainingAmount()).isZero();
        assertThat(calc.appliedAmount()).isZero();
    }

    @Test
    @DisplayName("resolveRuleSnapshot throws when a required rule code is missing")
    void resolveRuleSnapshot_throwsWhenRuleCodeMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("social insurance premium ruleCode");
    }

    private RuleSetSnapshot buildRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@2025.01-test",
            "2025.01",
            "test-hash",
            true,
            List.of(
                socialRule(
                    SocialInsurancePremiumCalculator.HEALTH_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "eligiblePaidAmountSource": "employeeHealthInsurancePremiumAmount",
                          "includesLongTermCare": true,
                          "excludeEmployerContribution": true
                        }
                        """
                ),
                socialRule(
                    SocialInsurancePremiumCalculator.EMPLOYMENT_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    """
                        {
                          "eligiblePaidAmountSource": "employeeEmploymentInsurancePremiumAmount",
                          "excludeEmployerContribution": true
                        }
                        """
                ),
                socialRule(
                    SocialInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE,
                    RuleCategory.LIMIT,
                    """
                        {
                          "capSource": "comprehensiveIncomeAmount",
                          "includedDeductionFamilies": ["인적공제", "연금보험료공제"]
                        }
                        """
                ),
                socialRule(
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
                        """
                )
            )
        );
    }

    private DeductionRule socialRule(String ruleCode, RuleCategory category, String params) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(DeductionType.SOCIAL_INSURANCE_PREMIUM);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(category);
        rule.setParameterJsonb(params);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        rule.setActive(true);
        return rule;
    }
}
