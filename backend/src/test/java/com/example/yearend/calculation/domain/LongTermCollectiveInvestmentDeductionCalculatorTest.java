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

class LongTermCollectiveInvestmentDeductionCalculatorTest {

    private LongTermCollectiveInvestmentDeductionCalculator calculator;
    private LongTermCollectiveInvestmentDeductionCalculator.LongTermCollectiveInvestmentRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new LongTermCollectiveInvestmentDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("납입액 없음: 소득공제 0")
    void noContribution_zeroDeductionAmount() {
        var result = calculator.calculate(0L, ruleSnapshot);

        assertThat(result.longTermCollectiveInvestmentDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("납입액 × 40% 적용 (한도 미만)")
    void contribution_fortyPercent_belowLimit() {
        // 납입액 300만 × 40% = 120만 (< 240만 한도)
        var result = calculator.calculate(3_000_000L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(1_200_000L);
        assertThat(result.longTermCollectiveInvestmentDeductionAmount()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("정확히 240만원 한도 도달: cap 미적용")
    void contributionExactlyAtLimit_noCapApplied() {
        // 납입액 600만 × 40% = 240만 (= 한도)
        var result = calculator.calculate(6_000_000L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(2_400_000L);
        assertThat(result.longTermCollectiveInvestmentDeductionAmount()).isEqualTo(2_400_000L);
    }

    @Test
    @DisplayName("240만원 한도 초과 시 cap 적용")
    void contributionAboveLimit_cappedAt2_4Million() {
        // 납입액 1000만 × 40% = 400만 → 한도 240만 cap
        var result = calculator.calculate(10_000_000L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(4_000_000L);
        assertThat(result.longTermCollectiveInvestmentDeductionAmount()).isEqualTo(2_400_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리")
    void negativeInput_treatedAsZero() {
        var result = calculator.calculate(-500_000L, ruleSnapshot);

        assertThat(result.longTermCollectiveInvestmentContributionAmount()).isZero();
        assertThat(result.longTermCollectiveInvestmentDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(LongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(LongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":2400000}"),
                rule(LongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}"),
                rule(LongTermCollectiveInvestmentDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"longTermCollectiveInvestmentContributionAmount\",\"longTermCollectiveInvestmentDeductionBeforeLimitAmount\",\"longTermCollectiveInvestmentDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
