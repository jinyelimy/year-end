package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForeignTaxCreditCalculatorTest {

    private ForeignTaxCreditCalculator calculator;
    private ForeignTaxCreditCalculator.ForeignTaxCreditRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new ForeignTaxCreditCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("모든 입력 0: 공제액 0")
    void zeroInputs_zeroCredit() {
        var result = calculator.calculate(0L, 0L, 0L, 0L, ruleSnapshot);

        assertThat(result.foreignTaxPaidAmount()).isZero();
        assertThat(result.foreignSourceIncomeAmount()).isZero();
        assertThat(result.foreignTaxCreditLimitAmount()).isZero();
        assertThat(result.foreignTaxCreditAmount()).isZero();
    }

    @Test
    @DisplayName("외국납부세액이 한도 미만: 외국납부세액 전액 공제")
    void paidBelowLimit_fullPaidCredited() {
        // calculatedTax=1,000,000; foreignSource=50,000,000; comprehensive=100,000,000
        // limit = 1,000,000 × (50,000,000 / 100,000,000) = 500,000
        // paid = 300,000 < limit → credit = 300,000
        var result = calculator.calculate(300_000L, 50_000_000L, 100_000_000L, 1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxCreditLimitAmount()).isEqualTo(500_000L);
        assertThat(result.foreignTaxCreditAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("외국납부세액이 한도 초과: 한도까지 캡")
    void paidAboveLimit_cappedAtLimit() {
        // calculatedTax=1,000,000; foreignSource=50,000,000; comprehensive=100,000,000
        // limit = 500,000
        // paid = 800,000 > limit → credit = 500,000
        var result = calculator.calculate(800_000L, 50_000_000L, 100_000_000L, 1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxCreditLimitAmount()).isEqualTo(500_000L);
        assertThat(result.foreignTaxCreditAmount()).isEqualTo(500_000L);
    }

    @Test
    @DisplayName("국외원천소득 0: 한도 0 → 공제 0")
    void foreignSourceIncomeZero_limitZero_creditZero() {
        var result = calculator.calculate(500_000L, 0L, 100_000_000L, 1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxCreditLimitAmount()).isZero();
        assertThat(result.foreignTaxCreditAmount()).isZero();
    }

    @Test
    @DisplayName("종합소득금액 0 (0-division): 한도 0")
    void comprehensiveIncomeZero_limitZero() {
        var result = calculator.calculate(500_000L, 50_000_000L, 0L, 1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxCreditLimitAmount()).isZero();
        assertThat(result.foreignTaxCreditAmount()).isZero();
    }

    @Test
    @DisplayName("국외원천소득 = 종합소득금액: 한도 = 산출세액 전액")
    void foreignSourceEqualsComprehensive_limitEqualsCalculatedTax() {
        var result = calculator.calculate(800_000L, 100_000_000L, 100_000_000L, 1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxCreditLimitAmount()).isEqualTo(1_000_000L);
        assertThat(result.foreignTaxCreditAmount()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 클램프")
    void negativeInputs_clampedToZero() {
        var result = calculator.calculate(-100_000L, -50_000_000L, -100_000_000L, -1_000_000L, ruleSnapshot);

        assertThat(result.foreignTaxPaidAmount()).isZero();
        assertThat(result.foreignSourceIncomeAmount()).isZero();
        assertThat(result.comprehensiveIncomeAmount()).isZero();
        assertThat(result.domesticCalculatedTaxAmount()).isZero();
        assertThat(result.foreignTaxCreditLimitAmount()).isZero();
        assertThat(result.foreignTaxCreditAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(ForeignTaxCreditCalculator.RATE_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(ForeignTaxCreditCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"creditRate\":1.00}"),
                rule(ForeignTaxCreditCalculator.LIMIT_FORMULA_RULE_CODE, RuleCategory.LIMIT,
                    "{\"formula\":\"calculatedTax * (foreignSourceIncome / comprehensiveIncome)\"}"),
                rule(ForeignTaxCreditCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"foreignTaxPaidAmount\",\"foreignSourceIncomeAmount\",\"foreignTaxCreditLimitAmount\",\"foreignTaxCreditAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.FOREIGN_TAX_CREDIT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
