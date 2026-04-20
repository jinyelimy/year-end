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

class SmeMutualAidDeductionCalculatorTest {

    private SmeMutualAidDeductionCalculator calculator;
    private SmeMutualAidDeductionCalculator.SmeMutualAidRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new SmeMutualAidDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("납입액 0: 소득공제 0")
    void noContribution_zeroDeductionAmount() {
        var result = calculator.calculate(0L, 30_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidDeductionAmount()).isZero();
        assertThat(result.smeMutualAidAppliedTierLimit()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("사업소득 4천만 이하 tier: 한도 500만 적용")
    void lowestTier_limit5Million() {
        // 사업소득 3천만 → tier limit 500만. 납입 300만 × 100% = 300만 (한도 미만)
        var result = calculator.calculate(3_000_000L, 30_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidAppliedTierLimit()).isEqualTo(5_000_000L);
        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(3_000_000L);
        assertThat(result.smeMutualAidDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("사업소득 4천만 정확히 경계: 한도 500만 유지 (≤40M)")
    void boundaryAt40Million_stillLowestTier() {
        // 사업소득 정확히 4천만 → tier limit 500만. 납입 600만 → 한도 500만 cap
        var result = calculator.calculate(6_000_000L, 40_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidAppliedTierLimit()).isEqualTo(5_000_000L);
        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(6_000_000L);
        assertThat(result.smeMutualAidDeductionAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("사업소득 4천만 초과 1억 이하 tier: 한도 300만 적용 + cap")
    void middleTier_limit3MillionWithCap() {
        // 사업소득 7천만 → tier limit 300만. 납입 500만 → 한도 300만 cap
        var result = calculator.calculate(5_000_000L, 70_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidAppliedTierLimit()).isEqualTo(3_000_000L);
        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(5_000_000L);
        assertThat(result.smeMutualAidDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("사업소득 1억 초과 tier: 한도 200만 적용 + cap")
    void highestTier_limit2MillionWithCap() {
        // 사업소득 2억 → tier limit 200만. 납입 400만 → 한도 200만 cap
        var result = calculator.calculate(4_000_000L, 200_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidAppliedTierLimit()).isEqualTo(2_000_000L);
        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(4_000_000L);
        assertThat(result.smeMutualAidDeductionAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리 (contribution, incomeBasis 둘 다)")
    void negativeInputs_treatedAsZero() {
        var result = calculator.calculate(-500_000L, -1_000_000L, ruleSnapshot);

        assertThat(result.smeMutualAidContributionAmount()).isZero();
        assertThat(result.smeMutualAidIncomeBasisAmount()).isZero();
        assertThat(result.smeMutualAidDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(SmeMutualAidDeductionCalculator.TIERED_LIMIT_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(SmeMutualAidDeductionCalculator.TIERED_LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"tiers\":["
                        + "{\"maxBusinessIncomeAmount\":40000000,\"annualDeductionLimit\":5000000},"
                        + "{\"maxBusinessIncomeAmount\":100000000,\"annualDeductionLimit\":3000000},"
                        + "{\"maxBusinessIncomeAmount\":null,\"annualDeductionLimit\":2000000}"
                        + "],\"eligibleRole\":\"SME_REPRESENTATIVE\"}"),
                rule(SmeMutualAidDeductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":1.00}"),
                rule(SmeMutualAidDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"smeMutualAidContributionAmount\",\"smeMutualAidIncomeBasisAmount\",\"smeMutualAidAppliedTierLimit\",\"smeMutualAidDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.SME_MUTUAL_AID);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
