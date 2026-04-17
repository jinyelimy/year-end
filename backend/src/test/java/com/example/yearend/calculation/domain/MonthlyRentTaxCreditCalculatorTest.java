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

class MonthlyRentTaxCreditCalculatorTest {

    private MonthlyRentTaxCreditCalculator calculator;
    private MonthlyRentTaxCreditCalculator.MonthlyRentRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new MonthlyRentTaxCreditCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("월세 없음: 세액공제 0")
    void noRent_zeroCreditAmount() {
        var result = calculator.calculate(0L, 50_000_000L, ruleSnapshot);

        assertThat(result.totalCreditAmount()).isZero();
    }

    @Test
    @DisplayName("총급여 8천만 초과: 공제 불가 (0원)")
    void grossSalaryAboveEligibilityLimit_zeroCreditAmount() {
        // 총급여 8100만 → 공제 불가
        var result = calculator.calculate(6_000_000L, 81_000_000L, ruleSnapshot);

        assertThat(result.eligibleRentAmount()).isZero();
        assertThat(result.totalCreditAmount()).isZero();
    }

    @Test
    @DisplayName("총급여 5500만 이하: 17% 공제율 적용")
    void grossSalaryBelowLowerThreshold_highRate() {
        // 월세 600만, 총급여 5000만 → 600만 × 17% = 1,020,000
        var result = calculator.calculate(6_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.eligibleRentAmount()).isEqualTo(6_000_000L);
        assertThat(result.creditRate()).isEqualTo(0.17);
        assertThat(result.totalCreditAmount()).isEqualTo(1_020_000L);
    }

    @Test
    @DisplayName("총급여 5500만 초과 8천만 이하: 15% 공제율 적용")
    void grossSalaryBetweenThresholds_lowRate() {
        // 월세 600만, 총급여 7000만 → 600만 × 15% = 900,000
        var result = calculator.calculate(6_000_000L, 70_000_000L, ruleSnapshot);

        assertThat(result.creditRate()).isEqualTo(0.15);
        assertThat(result.totalCreditAmount()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("총급여 5500만 경계값: 17% 적용")
    void grossSalaryAtThreshold_highRate() {
        var result = calculator.calculate(6_000_000L, 55_000_000L, ruleSnapshot);

        assertThat(result.creditRate()).isEqualTo(0.17);
        assertThat(result.totalCreditAmount()).isEqualTo(1_020_000L);
    }

    @Test
    @DisplayName("월세 1200만 한도 초과 시 cap 적용")
    void rentAboveAnnualLimit_cappedAt12Million() {
        // 월세 1500만 → 한도 1200만, 1200만 × 17% = 2,040,000
        var result = calculator.calculate(15_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.eligibleRentAmount()).isEqualTo(12_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(2_040_000L);
    }

    @Test
    @DisplayName("총급여 8천만 경계값: 공제 적용")
    void grossSalaryAtEligibilityLimit_applied() {
        // 총급여 정확히 8천만 → 공제 적용 (15%)
        var result = calculator.calculate(6_000_000L, 80_000_000L, ruleSnapshot);

        assertThat(result.totalCreditAmount()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("최대 공제액: 1200만 × 17% = 2,040,000")
    void maxCreditHighRate() {
        var result = calculator.calculate(20_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.eligibleRentAmount()).isEqualTo(12_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(2_040_000L);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(MonthlyRentTaxCreditCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"annualRentLimit\":12000000,\"grossSalaryEligibilityLimit\":80000000}"),
                rule(MonthlyRentTaxCreditCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"grossSalaryThreshold\":55000000,\"highRate\":0.17,\"lowRate\":0.15}"),
                rule(MonthlyRentTaxCreditCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"annualRentAmount\",\"eligibleRentAmount\",\"creditRate\",\"monthlyRentTaxCreditAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.MONTHLY_RENT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
