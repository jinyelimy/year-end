package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.IncomeTaxRateTableCalculator.IncomeTaxCalculation;
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

class IncomeTaxRateTableCalculatorTest {

    private final IncomeTaxRateTableCalculator calculator = new IncomeTaxRateTableCalculator(new ObjectMapper());

    @Test
    @DisplayName("calculates the official comprehensive income tax rate table boundaries from the rule snapshot")
    void calculatesOfficialRateTableBoundaries() {
        assertTax(0L, 0L);
        assertTax(14_000_000L, 840_000L);
        assertTax(14_000_001L, 840_000L);
        assertTax(50_000_000L, 6_240_000L);
        assertTax(50_000_001L, 6_240_000L);
        assertTax(88_000_000L, 15_360_000L);
        assertTax(150_000_000L, 37_060_000L);
        assertTax(300_000_000L, 94_060_000L);
        assertTax(500_000_000L, 174_060_000L);
        assertTax(1_000_000_000L, 384_060_000L);
        assertTax(1_000_000_001L, 384_060_000L);
    }

    @Test
    @DisplayName("keeps the applied bracket details for calculation trace")
    void keepsAppliedBracketDetails() {
        IncomeTaxCalculation calculation = calculator.calculate(
            30_000_000L,
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(calculation.calculatedTaxAmount()).isEqualTo(3_240_000L);
        assertThat(calculation.appliedBracket().sequence()).isEqualTo(2L);
        assertThat(calculation.appliedBracket().rate()).isEqualByComparingTo("0.15");
        assertThat(calculation.appliedBracket().quickDeductionAmount()).isEqualTo(1_260_000L);
        assertThat(calculation.effectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    @DisplayName("derives quick deduction amounts for legacy rate-only bracket parameters")
    void derivesQuickDeductionForRateOnlyParameters() {
        RuleSetSnapshot snapshot = new RuleSetSnapshot(
            "legacy",
            "2025.01",
            "legacy-hash",
            true,
            List.of(incomeTaxRule(
                """
                    {
                      "brackets": [
                        {"upTo":14000000,"rate":0.06},
                        {"upTo":50000000,"rate":0.15},
                        {"upTo":null,"rate":0.24}
                      ]
                    }
                    """
            ))
        );

        assertThat(calculator.calculate(30_000_000L, snapshot).calculatedTaxAmount()).isEqualTo(3_240_000L);
        assertThat(calculator.calculate(60_000_000L, snapshot).calculatedTaxAmount()).isEqualTo(8_640_000L);
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain the income tax rate table")
    void failsWhenRateTableRuleIsMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.calculate(30_000_000L, emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE);
    }

    private void assertTax(long taxableIncomeAmount, long expectedTaxAmount) {
        assertThat(calculator.calculate(
            taxableIncomeAmount,
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        ).calculatedTaxAmount()).isEqualTo(expectedTaxAmount);
    }

    private DeductionRule incomeTaxRule(String parameterJsonb) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(DeductionType.INCOME_TAX);
        rule.setRuleCode(IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE);
        rule.setRuleName(IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE);
        rule.setRuleVersion(1);
        rule.setRuleCategory(RuleCategory.BRACKET);
        rule.setParameterJsonb(parameterJsonb);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        rule.setActive(true);
        return rule;
    }
}
