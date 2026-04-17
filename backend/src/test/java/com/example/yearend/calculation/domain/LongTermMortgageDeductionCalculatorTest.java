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

class LongTermMortgageDeductionCalculatorTest {

    private LongTermMortgageDeductionCalculator calculator;
    private LongTermMortgageDeductionCalculator.LongTermMortgageRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new LongTermMortgageDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("이자상환액 없음: 소득공제 0")
    void noInterest_zeroDeductionAmount() {
        var result = calculator.calculate(0L, LongTermMortgageDeductionCalculator.TYPE_NON_INSTALLMENT_FIXED, ruleSnapshot);

        assertThat(result.longTermMortgageDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("NON_INSTALLMENT_FIXED: 한도 2000만원 적용")
    void nonInstallmentFixed_limit20M() {
        // 이자 1000만 < 한도 2000만 → 1000만 전액 공제
        var result = calculator.calculate(10_000_000L, LongTermMortgageDeductionCalculator.TYPE_NON_INSTALLMENT_FIXED, ruleSnapshot);

        assertThat(result.longTermMortgageLimitAmount()).isEqualTo(20_000_000L);
        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("NON_INSTALLMENT_FIXED: 한도 2000만 초과 시 cap 적용")
    void nonInstallmentFixed_capAt20M() {
        var result = calculator.calculate(25_000_000L, LongTermMortgageDeductionCalculator.TYPE_NON_INSTALLMENT_FIXED, ruleSnapshot);

        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(20_000_000L);
    }

    @Test
    @DisplayName("NON_INSTALLMENT_OR_FIXED: 한도 1800만원 적용")
    void nonInstallmentOrFixed_limit18M() {
        var result = calculator.calculate(20_000_000L, LongTermMortgageDeductionCalculator.TYPE_NON_INSTALLMENT_OR_FIXED, ruleSnapshot);

        assertThat(result.longTermMortgageLimitAmount()).isEqualTo(18_000_000L);
        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(18_000_000L);
    }

    @Test
    @DisplayName("OTHER_15Y: 한도 800만원 적용")
    void other15Y_limit8M() {
        var result = calculator.calculate(5_000_000L, LongTermMortgageDeductionCalculator.TYPE_OTHER_15Y, ruleSnapshot);

        assertThat(result.longTermMortgageLimitAmount()).isEqualTo(8_000_000L);
        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(5_000_000L);
    }

    @Test
    @DisplayName("OTHER_15Y: 한도 800만 초과 시 cap")
    void other15Y_capAt8M() {
        var result = calculator.calculate(10_000_000L, LongTermMortgageDeductionCalculator.TYPE_OTHER_15Y, ruleSnapshot);

        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(8_000_000L);
    }

    @Test
    @DisplayName("UNDER_15Y: 한도 600만원 적용")
    void under15Y_limit6M() {
        var result = calculator.calculate(4_000_000L, LongTermMortgageDeductionCalculator.TYPE_UNDER_15Y, ruleSnapshot);

        assertThat(result.longTermMortgageLimitAmount()).isEqualTo(6_000_000L);
        assertThat(result.longTermMortgageDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("알 수 없는 유형(NONE): 0원 반환")
    void unknownType_zeroDeductionAmount() {
        var result = calculator.calculate(5_000_000L, "NONE", ruleSnapshot);

        assertThat(result.longTermMortgageLimitAmount()).isZero();
        assertThat(result.longTermMortgageDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("null 유형: 0원 반환")
    void nullType_zeroDeductionAmount() {
        var result = calculator.calculate(5_000_000L, null, ruleSnapshot);

        assertThat(result.longTermMortgageDeductionAmount()).isZero();
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(LongTermMortgageDeductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"tiers\":[" +
                    "{\"repaymentType\":\"NON_INSTALLMENT_FIXED\",\"limitAmount\":20000000}," +
                    "{\"repaymentType\":\"NON_INSTALLMENT_OR_FIXED\",\"limitAmount\":18000000}," +
                    "{\"repaymentType\":\"OTHER_15Y\",\"limitAmount\":8000000}," +
                    "{\"repaymentType\":\"UNDER_15Y\",\"limitAmount\":6000000}" +
                    "]}"),
                rule(LongTermMortgageDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"longTermMortgageInterestAmount\",\"longTermMortgageRepaymentType\",\"longTermMortgageLimitAmount\",\"longTermMortgageDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.LONG_TERM_MORTGAGE);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
