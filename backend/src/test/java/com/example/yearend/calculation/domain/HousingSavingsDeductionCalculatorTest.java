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

class HousingSavingsDeductionCalculatorTest {

    private HousingSavingsDeductionCalculator calculator;
    private HousingSavingsDeductionCalculator.HousingSavingsRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new HousingSavingsDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("납입액 없음: 소득공제 0")
    void noContribution_zeroDeductionAmount() {
        var result = calculator.calculate(0L, ruleSnapshot);

        assertThat(result.housingSavingsDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("납입액 × 40% 적용")
    void contribution_fortyPercent() {
        // 납입액 240만 × 40% = 96만
        var result = calculator.calculate(2_400_000L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(960_000L);
        assertThat(result.housingSavingsDeductionAmount()).isEqualTo(960_000L);
    }

    @Test
    @DisplayName("300만원 한도 초과 시 cap 적용")
    void contributionAboveLimit_cappedAt3Million() {
        // 납입액 1000만 × 40% = 400만 → 한도 300만 cap
        var result = calculator.calculate(10_000_000L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(4_000_000L);
        assertThat(result.housingSavingsDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("정확히 300만원 한도 도달: cap 미적용")
    void contributionExactlyAtLimit_noCapApplied() {
        // 납입액 750만 × 40% = 300만
        var result = calculator.calculate(7_500_000L, ruleSnapshot);

        assertThat(result.housingSavingsDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("최대 공제액: 300만원")
    void maxDeductionAmount_3Million() {
        var result = calculator.calculate(20_000_000L, ruleSnapshot);

        assertThat(result.housingSavingsDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리")
    void negativeInput_treatedAsZero() {
        var result = calculator.calculate(-500_000L, ruleSnapshot);

        assertThat(result.housingSavingsContributionAmount()).isZero();
        assertThat(result.housingSavingsDeductionAmount()).isZero();
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(HousingSavingsDeductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":3000000}"),
                rule(HousingSavingsDeductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}"),
                rule(HousingSavingsDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"housingSavingsContributionAmount\",\"deductionBeforeLimitAmount\",\"housingSavingsDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.HOUSING_SAVINGS);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
