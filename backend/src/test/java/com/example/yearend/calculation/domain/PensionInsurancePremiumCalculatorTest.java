package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.PensionInsurancePremiumCalculator.PensionInsurancePremiumCalculation;
import com.example.yearend.calculation.domain.PensionInsurancePremiumCalculator.PensionInsurancePremiumRuleSnapshot;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PensionInsurancePremiumCalculatorTest {

    private final PensionInsurancePremiumCalculator calculator = new PensionInsurancePremiumCalculator(new ObjectMapper());

    @Test
    @DisplayName("resolves pension insurance premium rule snapshot with confirmed parameters")
    void resolvesRuleSnapshot() {
        PensionInsurancePremiumRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(ruleSnapshot.eligibilityRule().ruleCode())
            .isEqualTo(PensionInsurancePremiumCalculator.ELIGIBILITY_RULE_CODE);
        assertThat(ruleSnapshot.excludeEmployerContribution()).isTrue();
        assertThat(ruleSnapshot.paidAmountFloorAtZero()).isTrue();
        assertThat(ruleSnapshot.includedDeductionFamilies()).contains(
            "PERSONAL_DEDUCTION",
            "PENSION_INSURANCE_PREMIUM",
            "REVERSE_MORTGAGE_INTEREST",
            "SPECIAL_INCOME_DEDUCTION",
            "TAX_SPECIAL_LAW_INCOME_DEDUCTION"
        );
        assertThat(ruleSnapshot.traceFields()).contains(
            "eligiblePaidAmount",
            "appliedAmount",
            "aggregateCapRemainingAmount"
        );
    }

    @Test
    @DisplayName("applies the full eligible paid amount when comprehensive income covers it")
    void appliesFullEligibleAmountWhenCapAllows() {
        PensionInsurancePremiumRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PensionInsurancePremiumCalculation calculation = calculator.calculate(
            3_600_000L,
            50_000_000L,
            1_500_000L,
            ruleSnapshot
        );

        assertThat(calculation.eligiblePaidAmount()).isEqualTo(3_600_000L);
        assertThat(calculation.appliedAmount()).isEqualTo(3_600_000L);
        assertThat(calculation.aggregateCapRemainingAmount()).isEqualTo(48_500_000L);
    }

    @Test
    @DisplayName("floors the eligible paid amount at zero when input is negative")
    void floorsNegativeEligibleAmount() {
        PensionInsurancePremiumRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PensionInsurancePremiumCalculation calculation = calculator.calculate(
            -10_000L,
            50_000_000L,
            0L,
            ruleSnapshot
        );

        assertThat(calculation.eligiblePaidAmount()).isZero();
        assertThat(calculation.appliedAmount()).isZero();
    }

    @Test
    @DisplayName("caps applied amount by remaining comprehensive income after prior deductions")
    void capsByAggregateRemaining() {
        PensionInsurancePremiumRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PensionInsurancePremiumCalculation calculation = calculator.calculate(
            5_000_000L,
            10_000_000L,
            8_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.aggregateCapRemainingAmount()).isEqualTo(2_000_000L);
        assertThat(calculation.appliedAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("produces zero applied amount when prior deductions already exhaust comprehensive income")
    void zeroAppliedWhenCapExhausted() {
        PensionInsurancePremiumRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PensionInsurancePremiumCalculation calculation = calculator.calculate(
            3_000_000L,
            5_000_000L,
            6_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.aggregateCapRemainingAmount()).isZero();
        assertThat(calculation.appliedAmount()).isZero();
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain pension insurance premium rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PensionInsurancePremiumCalculator.ELIGIBILITY_RULE_CODE);
    }
}
