package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.CreditCardDeductionCalculator.CreditCardCalculation;
import com.example.yearend.calculation.domain.CreditCardDeductionCalculator.CreditCardRuleSnapshot;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditCardDeductionCalculatorTest {

    private final CreditCardDeductionCalculator calculator = new CreditCardDeductionCalculator(new ObjectMapper());

    @Test
    @DisplayName("resolves credit card deduction rule snapshot with confirmed parameters")
    void resolvesRuleSnapshot() {
        CreditCardRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(ruleSnapshot.minimumUsageThresholdRule().ruleCode())
            .isEqualTo(CreditCardDeductionCalculator.MINIMUM_USAGE_THRESHOLD_RULE_CODE);
        assertThat(ruleSnapshot.thresholdRate()).isEqualTo(0.25);
        assertThat(ruleSnapshot.creditRate()).isEqualTo(0.15);
        assertThat(ruleSnapshot.debitCashRate()).isEqualTo(0.30);
        assertThat(ruleSnapshot.tiers()).hasSize(3);
        assertThat(ruleSnapshot.traceFields()).contains("totalUsageAmount", "appliedAmount");
    }

    @Test
    @DisplayName("returns zero deduction when total usage is below the minimum threshold")
    void returnsZeroWhenTotalUsageBelowThreshold() {
        CreditCardRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // threshold = 40M * 25% = 10M; total = 5M + 3M = 8M < 10M
        CreditCardCalculation calculation = calculator.calculate(
            5_000_000L,
            3_000_000L,
            0L,
            0L,
            40_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.appliedAmount()).isZero();
        assertThat(calculation.creditCardAboveThresholdAmount()).isZero();
        assertThat(calculation.debitCashZeroPayAboveThresholdAmount()).isZero();
        assertThat(calculation.minimumUsageThresholdAmount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("deducts credit card amount above the threshold at the 15% credit rate")
    void deductsCreditCardOnlyAboveThreshold() {
        CreditCardRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // threshold = 40M * 25% = 10M; credit = 16M → above = 6M; deduction = 6M * 15% = 900,000
        CreditCardCalculation calculation = calculator.calculate(
            16_000_000L,
            0L,
            0L,
            0L,
            40_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.creditCardAboveThresholdAmount()).isEqualTo(6_000_000L);
        assertThat(calculation.debitCashZeroPayAboveThresholdAmount()).isZero();
        assertThat(calculation.deductionBeforeLimitAmount()).isEqualTo(900_000L);
        assertThat(calculation.basicLimitAmount()).isEqualTo(3_000_000L);
        assertThat(calculation.appliedAmount()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("deducts debit/cash/zeropay above the threshold at the 30% rate after credit card exhausts it")
    void deductsDebitCashAboveThresholdAfterCreditExhaustsIt() {
        CreditCardRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // threshold = 40M * 25% = 10M; credit = 10M (exactly threshold), debit = 5M (all above)
        // deduction = 0 * 15% + 5M * 30% = 1,500,000
        CreditCardCalculation calculation = calculator.calculate(
            10_000_000L,
            5_000_000L,
            0L,
            0L,
            40_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.creditCardAboveThresholdAmount()).isZero();
        assertThat(calculation.debitCashZeroPayAboveThresholdAmount()).isEqualTo(5_000_000L);
        assertThat(calculation.deductionBeforeLimitAmount()).isEqualTo(1_500_000L);
        assertThat(calculation.appliedAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("caps the deduction at the tier-based annual limit when before-limit amount exceeds it")
    void capsAtTierBasedLimit() {
        CreditCardRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // salary = 80M → tier 2 (≤120M) → limit = 2,500,000
        // threshold = 80M * 25% = 20M; credit = 40M → above = 20M; deduction = 20M * 15% = 3,000,000
        // appliedAmount = min(3,000,000, 2,500,000) = 2,500,000
        CreditCardCalculation calculation = calculator.calculate(
            40_000_000L,
            0L,
            0L,
            0L,
            80_000_000L,
            ruleSnapshot
        );

        assertThat(calculation.deductionBeforeLimitAmount()).isEqualTo(3_000_000L);
        assertThat(calculation.basicLimitAmount()).isEqualTo(2_500_000L);
        assertThat(calculation.appliedAmount()).isEqualTo(2_500_000L);
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain credit card deduction rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(CreditCardDeductionCalculator.MINIMUM_USAGE_THRESHOLD_RULE_CODE);
    }
}
