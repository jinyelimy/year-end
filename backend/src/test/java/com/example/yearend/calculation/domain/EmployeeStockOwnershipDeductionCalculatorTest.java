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

class EmployeeStockOwnershipDeductionCalculatorTest {

    private EmployeeStockOwnershipDeductionCalculator calculator;
    private EmployeeStockOwnershipDeductionCalculator.EmployeeStockOwnershipRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new EmployeeStockOwnershipDeductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("출연 0: 소득공제 0")
    void noContribution_zeroDeductionAmount() {
        var result = calculator.calculate(0L, false, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isZero();
        assertThat(result.employeeStockOwnershipDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("일반 기업 출연 300만원: 공제 300만원 (한도 400만원 이내)")
    void regularEmployer_3Million_withinLimit() {
        var result = calculator.calculate(3_000_000L, false, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isEqualTo(3_000_000L);
        assertThat(result.employeeStockOwnershipAppliedLimitAmount()).isEqualTo(4_000_000L);
        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("일반 기업 출연 500만원: 한도 400만원 캡")
    void regularEmployer_5Million_cappedAtLimit() {
        var result = calculator.calculate(5_000_000L, false, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isEqualTo(5_000_000L);
        assertThat(result.employeeStockOwnershipAppliedLimitAmount()).isEqualTo(4_000_000L);
        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("벤처 기업 출연 1천만원: 공제 1천만원 (한도 1500만원 이내)")
    void ventureEmployer_10Million_withinLimit() {
        var result = calculator.calculate(10_000_000L, true, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isEqualTo(10_000_000L);
        assertThat(result.employeeStockOwnershipAppliedLimitAmount()).isEqualTo(15_000_000L);
        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("벤처 기업 출연 2천만원: 한도 1500만원 캡")
    void ventureEmployer_20Million_cappedAtLimit() {
        var result = calculator.calculate(20_000_000L, true, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isEqualTo(20_000_000L);
        assertThat(result.employeeStockOwnershipAppliedLimitAmount()).isEqualTo(15_000_000L);
        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(15_000_000L);
    }

    @Test
    @DisplayName("일반 기업 출연 정확히 400만원 경계: 공제 400만원")
    void regularEmployer_exactlyAtLimit() {
        var result = calculator.calculate(4_000_000L, false, ruleSnapshot);

        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("벤처 기업 출연 정확히 1500만원 경계: 공제 1500만원")
    void ventureEmployer_exactlyAtLimit() {
        var result = calculator.calculate(15_000_000L, true, ruleSnapshot);

        assertThat(result.employeeStockOwnershipDeductionAmount()).isEqualTo(15_000_000L);
    }

    @Test
    @DisplayName("음수 입력 → 0으로 처리")
    void negativeInput_treatedAsZero() {
        var result = calculator.calculate(-500_000L, false, ruleSnapshot);

        assertThat(result.employeeStockOwnershipContributionAmount()).isZero();
        assertThat(result.employeeStockOwnershipDeductionAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(EmployeeStockOwnershipDeductionCalculator.RATE_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(EmployeeStockOwnershipDeductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"deductionRate\":1.00}"),
                rule(EmployeeStockOwnershipDeductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"regularAnnualDeductionLimit\":4000000,\"ventureAnnualDeductionLimit\":15000000}"),
                rule(EmployeeStockOwnershipDeductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"employeeStockOwnershipContributionAmount\",\"employeeStockOwnershipAppliedLimitAmount\",\"employeeStockOwnershipDeductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.EMPLOYEE_STOCK_OWNERSHIP);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
