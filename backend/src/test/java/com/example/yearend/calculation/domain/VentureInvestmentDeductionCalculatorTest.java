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

class VentureInvestmentDeductionCalculatorTest {

    private static final long LARGE_INCOME = 500_000_000L; // income large enough so 50% cap never binds

    private VentureInvestmentDeductionCalculator calculator;
    private VentureInvestmentDeductionCalculator.VentureInvestmentRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new VentureInvestmentDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("납입 0: 소득공제 0")
    void noContribution_zeroDeductionAmount() {
        var result = calculator.calculate(0L, 0L, LARGE_INCOME, ruleSnapshot);

        assertThat(result.ventureInvestmentDeductionAmount()).isZero();
        assertThat(result.ventureDirectDeductionAmount()).isZero();
        assertThat(result.ventureFundDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("직접투자 2천만원 (모두 100% 구간): 공제 2천만원")
    void directOnly_20Million_withinFirstTier() {
        var result = calculator.calculate(20_000_000L, 0L, LARGE_INCOME, ruleSnapshot);

        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(20_000_000L);
        assertThat(result.ventureFundDeductionAmount()).isZero();
        assertThat(result.ventureDeductionBeforeIncomeCapAmount()).isEqualTo(20_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(20_000_000L);
    }

    @Test
    @DisplayName("직접투자 4천만원 (3천만@100% + 1천만@70%): 공제 37백만원")
    void directOnly_40Million_crossesSecondTier() {
        var result = calculator.calculate(40_000_000L, 0L, LARGE_INCOME, ruleSnapshot);

        // 30M * 1.00 + 10M * 0.70 = 30M + 7M = 37M
        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(37_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(37_000_000L);
    }

    @Test
    @DisplayName("직접투자 6천만원 (3천만@100% + 2천만@70% + 1천만@30%): 공제 47백만원")
    void directOnly_60Million_crossesAllThreeTiers() {
        var result = calculator.calculate(60_000_000L, 0L, LARGE_INCOME, ruleSnapshot);

        // 30M * 1.00 + 20M * 0.70 + 10M * 0.30 = 30M + 14M + 3M = 47M
        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(47_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(47_000_000L);
    }

    @Test
    @DisplayName("조합 출자 1천만원만: 공제 1백만원 (10%)")
    void fundOnly_10Million() {
        var result = calculator.calculate(0L, 10_000_000L, LARGE_INCOME, ruleSnapshot);

        assertThat(result.ventureDirectDeductionAmount()).isZero();
        assertThat(result.ventureFundDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("직접 2천만 + 조합 1천만: 공제 2천1백만원 (2천만 + 1백만)")
    void combinedDirectAndFund() {
        var result = calculator.calculate(20_000_000L, 10_000_000L, LARGE_INCOME, ruleSnapshot);

        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(20_000_000L);
        assertThat(result.ventureFundDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(result.ventureDeductionBeforeIncomeCapAmount()).isEqualTo(21_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(21_000_000L);
    }

    @Test
    @DisplayName("종합소득 50% 한도가 바인딩: 직접 6천만 but 소득 2천만 → 1천만 cap")
    void incomeCapBinds() {
        // direct 60M → before-cap 47M
        // income 20M → 50% cap = 10M
        var result = calculator.calculate(60_000_000L, 0L, 20_000_000L, ruleSnapshot);

        assertThat(result.ventureDeductionBeforeIncomeCapAmount()).isEqualTo(47_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("정확히 tier 경계 3천만: 모두 100% 구간 → 공제 3천만")
    void boundaryAt30Million() {
        var result = calculator.calculate(30_000_000L, 0L, LARGE_INCOME, ruleSnapshot);

        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(30_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(30_000_000L);
    }

    @Test
    @DisplayName("정확히 tier 경계 5천만: 3천만@100% + 2천만@70% → 공제 44백만")
    void boundaryAt50Million() {
        var result = calculator.calculate(50_000_000L, 0L, LARGE_INCOME, ruleSnapshot);

        // 30M + 20M * 0.70 = 30M + 14M = 44M
        assertThat(result.ventureDirectDeductionAmount()).isEqualTo(44_000_000L);
        assertThat(result.ventureInvestmentDeductionAmount()).isEqualTo(44_000_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리")
    void negativeInputs_treatedAsZero() {
        var result = calculator.calculate(-500_000L, -1_000_000L, -200_000L, ruleSnapshot);

        assertThat(result.ventureDirectInvestmentAmount()).isZero();
        assertThat(result.ventureFundInvestmentAmount()).isZero();
        assertThat(result.ventureInvestmentDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(VentureInvestmentDeductionCalculator.RATE_DIRECT_TIERED_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(VentureInvestmentDeductionCalculator.RATE_DIRECT_TIERED_RULE_CODE, RuleCategory.FORMULA,
                    "{\"tiers\":["
                        + "{\"maxContributionAmount\":30000000,\"deductionRate\":1.00},"
                        + "{\"maxContributionAmount\":50000000,\"deductionRate\":0.70},"
                        + "{\"maxContributionAmount\":null,\"deductionRate\":0.30}"
                        + "]}"),
                rule(VentureInvestmentDeductionCalculator.RATE_FUND_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":0.10}"),
                rule(VentureInvestmentDeductionCalculator.INCOME_LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"comprehensiveIncomeLimitRate\":0.50}"),
                rule(VentureInvestmentDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"ventureDirectInvestmentAmount\",\"ventureFundInvestmentAmount\",\"ventureDirectDeductionAmount\",\"ventureFundDeductionAmount\",\"ventureDeductionBeforeIncomeCapAmount\",\"ventureInvestmentDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.VENTURE_INVESTMENT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
