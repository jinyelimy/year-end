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

class TaxPayerAssociationCreditCalculatorTest {

    private TaxPayerAssociationCreditCalculator calculator;
    private TaxPayerAssociationCreditCalculator.TaxPayerAssociationCreditRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new TaxPayerAssociationCreditCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("모든 입력 0: 공제액 0")
    void zeroInputs_zeroCredit() {
        var result = calculator.calculate(0L, 0L, 0L, ruleSnapshot);

        assertThat(result.taxPayerAssociationIncomeAmount()).isZero();
        assertThat(result.earnedIncomeAmount()).isZero();
        assertThat(result.domesticCalculatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationCreditAmount()).isZero();
    }

    @Test
    @DisplayName("일반 케이스: 근로 50M, 납세조합 10M, 산출세액 5M → 배분세액 1M, 공제 50,000")
    void normalCase_partialAssociation() {
        // associationIncome=10M, earnedIncome=50M, calcTax=5M
        // allocated = 5,000,000 × (10,000,000 / 50,000,000) = 1,000,000
        // credit = 1,000,000 × 0.05 = 50,000
        var result = calculator.calculate(10_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isEqualTo(1_000_000L);
        assertThat(result.taxPayerAssociationCreditAmount()).isEqualTo(50_000L);
    }

    @Test
    @DisplayName("납세조합 소득 = 근로소득: 배분세액 = 산출세액, 공제 = 산출세액 × 5%")
    void associationEqualsEarned_fullCredit() {
        // associationIncome=earnedIncome → ratio=1.0 → allocated=calcTax
        // credit = 5,000,000 × 0.05 = 250,000
        var result = calculator.calculate(50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isEqualTo(5_000_000L);
        assertThat(result.taxPayerAssociationCreditAmount()).isEqualTo(250_000L);
    }

    @Test
    @DisplayName("근로소득금액 0 (0-division 방어): 공제 0")
    void earnedIncomeZero_divisionGuard_zeroCredit() {
        var result = calculator.calculate(10_000_000L, 0L, 5_000_000L, ruleSnapshot);

        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationCreditAmount()).isZero();
    }

    @Test
    @DisplayName("납세조합 > 근로소득 (비정상 비율): 비율 1 이상도 계산 수행")
    void associationGreaterThanEarned_computesAsIs() {
        // associationIncome=60M, earnedIncome=50M → ratio=1.2
        // allocated = 5,000,000 × 1.2 = 6,000,000
        // credit = 6,000,000 × 0.05 = 300,000
        var result = calculator.calculate(60_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isEqualTo(6_000_000L);
        assertThat(result.taxPayerAssociationCreditAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 클램프")
    void negativeInputs_clampedToZero() {
        var result = calculator.calculate(-10_000_000L, -50_000_000L, -5_000_000L, ruleSnapshot);

        assertThat(result.taxPayerAssociationIncomeAmount()).isZero();
        assertThat(result.earnedIncomeAmount()).isZero();
        assertThat(result.domesticCalculatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationCreditAmount()).isZero();
    }

    @Test
    @DisplayName("산출세액 0: 배분세액 0, 공제 0")
    void calculatedTaxZero_zeroCredit() {
        var result = calculator.calculate(10_000_000L, 50_000_000L, 0L, ruleSnapshot);

        assertThat(result.taxPayerAssociationAllocatedTaxAmount()).isZero();
        assertThat(result.taxPayerAssociationCreditAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(TaxPayerAssociationCreditCalculator.RATE_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(TaxPayerAssociationCreditCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"creditRate\":0.05}"),
                rule(TaxPayerAssociationCreditCalculator.FORMULA_RULE_CODE, RuleCategory.FORMULA,
                    "{\"formula\":\"calculatedTax * (associationIncome / earnedIncome) * creditRate\"}"),
                rule(TaxPayerAssociationCreditCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"taxPayerAssociationIncomeAmount\",\"earnedIncomeAmount\",\"domesticCalculatedTaxAmount\",\"taxPayerAssociationAllocatedTaxAmount\",\"taxPayerAssociationCreditAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.TAX_PAYER_ASSOCIATION_CREDIT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
