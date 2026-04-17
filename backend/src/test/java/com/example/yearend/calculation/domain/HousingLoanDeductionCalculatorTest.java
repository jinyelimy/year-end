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

class HousingLoanDeductionCalculatorTest {

    private HousingLoanDeductionCalculator calculator;
    private HousingLoanDeductionCalculator.HousingLoanRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new HousingLoanDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("상환액 없음: 소득공제 0")
    void noRepayment_zeroDeductionAmount() {
        var result = calculator.calculate(0L, 0L, ruleSnapshot);

        assertThat(result.housingLoanDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("금융기관 차입만: 원리금 × 40%")
    void bankOnlyRepayment_fortyPercent() {
        // 금융기관 원리금 500만 × 40% = 200만
        var result = calculator.calculate(5_000_000L, 0L, ruleSnapshot);

        assertThat(result.totalRepaymentAmount()).isEqualTo(5_000_000L);
        assertThat(result.housingLoanDeductionAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("개인 차입만: 원금 × 40%")
    void individualOnlyRepayment_fortyPercent() {
        // 개인 원금 300만 × 40% = 120만
        var result = calculator.calculate(0L, 3_000_000L, ruleSnapshot);

        assertThat(result.totalRepaymentAmount()).isEqualTo(3_000_000L);
        assertThat(result.housingLoanDeductionAmount()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("금융기관 + 개인 혼합: 합산 후 40%")
    void mixedRepayment_combinedFortyPercent() {
        // 금융기관 300만 + 개인 200만 = 500만 × 40% = 200만
        var result = calculator.calculate(3_000_000L, 2_000_000L, ruleSnapshot);

        assertThat(result.totalRepaymentAmount()).isEqualTo(5_000_000L);
        assertThat(result.housingLoanDeductionAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("400만원 한도 초과 시 cap 적용")
    void repaymentAboveLimit_cappedAt4Million() {
        // 금융기관 1500만 × 40% = 600만 → 한도 400만 cap
        var result = calculator.calculate(15_000_000L, 0L, ruleSnapshot);

        assertThat(result.deductionBeforeLimitAmount()).isEqualTo(6_000_000L);
        assertThat(result.housingLoanDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("정확히 400만원 한도: cap 미적용")
    void repaymentExactlyAtLimit_noCapApplied() {
        // 금융기관 1000만 × 40% = 400만 → 한도 정확히 맞음
        var result = calculator.calculate(10_000_000L, 0L, ruleSnapshot);

        assertThat(result.housingLoanDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("최대 공제액: 한도 400만원")
    void maxDeductionAmount_4Million() {
        var result = calculator.calculate(20_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.housingLoanDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리")
    void negativeInput_treatedAsZero() {
        var result = calculator.calculate(-100L, -200L, ruleSnapshot);

        assertThat(result.totalRepaymentAmount()).isZero();
        assertThat(result.housingLoanDeductionAmount()).isZero();
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(HousingLoanDeductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"annualDeductionLimit\":4000000}"),
                rule(HousingLoanDeductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":0.40}"),
                rule(HousingLoanDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"housingLoanBankRepaymentAmount\",\"housingLoanIndividualRepaymentAmount\",\"totalRepaymentAmount\",\"deductionBeforeLimitAmount\",\"housingLoanDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.HOUSING_LOAN);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
