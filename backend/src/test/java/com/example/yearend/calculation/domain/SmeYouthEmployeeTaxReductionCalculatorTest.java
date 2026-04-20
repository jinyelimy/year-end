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

class SmeYouthEmployeeTaxReductionCalculatorTest {

    private SmeYouthEmployeeTaxReductionCalculator calculator;
    private SmeYouthEmployeeTaxReductionCalculator.SmeYouthEmployeeTaxReductionRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new SmeYouthEmployeeTaxReductionCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("category null → 감면 0")
    void categoryNull_zeroReduction() {
        var result = calculator.calculate(null, 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isZero();
        assertThat(result.smeYouthAllocatedTaxAmount()).isZero();
    }

    @Test
    @DisplayName("category NONE (알 수 없는 값) → 감면 0")
    void categoryNone_zeroReduction() {
        var result = calculator.calculate("NONE", 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isZero();
    }

    @Test
    @DisplayName("YOUTH 케이스: 배분세액 5M × 0.9 = 4.5M → 연간한도 2M 로 cap")
    void youthCategory_cappedByAnnualLimit() {
        // earnedIncome=50M, employerIncome=50M → ratio=1.0, allocated=5M
        // 5M × 0.9 = 4,500,000 → cap 2,000,000
        var result = calculator.calculate("YOUTH", 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthAllocatedTaxAmount()).isEqualTo(5_000_000L);
        assertThat(result.smeYouthReductionRate()).isEqualTo(0.90);
        assertThat(result.smeYouthAnnualLimitAmount()).isEqualTo(2_000_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(2_000_000L);
    }

    @Test
    @DisplayName("YOUTH 케이스: 배분세액 1M × 0.9 = 900,000 (연간한도 2M 미만)")
    void youthCategory_belowAnnualLimit() {
        // earnedIncome=50M, employerIncome=50M → ratio=1.0, allocated=1M
        // 1M × 0.9 = 900,000 (한도 2M 미만)
        var result = calculator.calculate("YOUTH", 50_000_000L, 50_000_000L, 1_000_000L, ruleSnapshot);

        assertThat(result.smeYouthAllocatedTaxAmount()).isEqualTo(1_000_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("SENIOR 케이스: rate 0.7, 연간한도 1.5M")
    void seniorCategory_rateAndLimit() {
        // allocated=5M, 5M × 0.7 = 3,500,000 → cap 1,500,000
        var result = calculator.calculate("SENIOR", 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthReductionRate()).isEqualTo(0.70);
        assertThat(result.smeYouthAnnualLimitAmount()).isEqualTo(1_500_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("DISABLED 케이스: rate 0.7, 연간한도 1.5M")
    void disabledCategory_rateAndLimit() {
        var result = calculator.calculate("DISABLED", 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthReductionRate()).isEqualTo(0.70);
        assertThat(result.smeYouthAnnualLimitAmount()).isEqualTo(1_500_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("CAREER_INTERRUPTED_WOMAN 케이스: rate 0.7, 연간한도 1.5M")
    void careerInterruptedWomanCategory_rateAndLimit() {
        var result = calculator.calculate("CAREER_INTERRUPTED_WOMAN", 50_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthReductionRate()).isEqualTo(0.70);
        assertThat(result.smeYouthAnnualLimitAmount()).isEqualTo(1_500_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("employerIncome 부분 (20M / 50M = 0.4): 배분세액 = 산출세액 × 0.4")
    void partialEmployerIncome_allocatesProportionally() {
        // employerIncome=20M, earnedIncome=50M → ratio=0.4
        // allocated = 5M × 0.4 = 2,000,000
        // 2M × 0.9 = 1,800,000 (한도 2M 미만)
        var result = calculator.calculate("YOUTH", 20_000_000L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthAllocatedTaxAmount()).isEqualTo(2_000_000L);
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isEqualTo(1_800_000L);
    }

    @Test
    @DisplayName("employerIncome 0 → 감면 0")
    void employerIncomeZero_zeroReduction() {
        var result = calculator.calculate("YOUTH", 0L, 50_000_000L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthAllocatedTaxAmount()).isZero();
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isZero();
    }

    @Test
    @DisplayName("earnedIncome 0 (zero-division 방어) → 감면 0")
    void earnedIncomeZero_divisionGuard_zeroReduction() {
        var result = calculator.calculate("YOUTH", 50_000_000L, 0L, 5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthAllocatedTaxAmount()).isZero();
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isZero();
    }

    @Test
    @DisplayName("음수 입력 → 0으로 클램프")
    void negativeInputs_clampedToZero() {
        var result = calculator.calculate("YOUTH", -50_000_000L, -50_000_000L, -5_000_000L, ruleSnapshot);

        assertThat(result.smeYouthEmployerIncomeAmount()).isZero();
        assertThat(result.smeYouthAllocatedTaxAmount()).isZero();
        assertThat(result.smeYouthEmployeeTaxReductionAmount()).isZero();
    }

    @Test
    @DisplayName("필수 rule 누락 시 IllegalStateException")
    void failsWhenRuleMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(SmeYouthEmployeeTaxReductionCalculator.RATE_RULE_CODE);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(SmeYouthEmployeeTaxReductionCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"rateByCategory\":{\"YOUTH\":0.90,\"SENIOR\":0.70,\"DISABLED\":0.70,\"CAREER_INTERRUPTED_WOMAN\":0.70}}"),
                rule(SmeYouthEmployeeTaxReductionCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"annualLimitByCategory\":{\"YOUTH\":2000000,\"SENIOR\":1500000,\"DISABLED\":1500000,\"CAREER_INTERRUPTED_WOMAN\":1500000}}"),
                rule(SmeYouthEmployeeTaxReductionCalculator.ELIGIBILITY_RULE_CODE, RuleCategory.ELIGIBILITY,
                    "{\"youthMinAge\":15,\"youthMaxAge\":34,\"seniorMinAge\":60,\"reductionYearsByCategory\":{\"YOUTH\":5,\"SENIOR\":3,\"DISABLED\":3,\"CAREER_INTERRUPTED_WOMAN\":3},\"militaryServiceDeductionMaxYears\":6,\"requiresSmeEmployer\":true}"),
                rule(SmeYouthEmployeeTaxReductionCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"smeYouthCategory\",\"smeYouthEmployerIncomeAmount\",\"smeYouthAllocatedTaxAmount\",\"smeYouthReductionRate\",\"smeYouthAnnualLimitAmount\",\"smeYouthEmployeeTaxReductionAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.SME_YOUTH_EMPLOYEE_TAX_REDUCTION);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
