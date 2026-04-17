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

class PensionAccountTaxCreditCalculatorTest {

    private PensionAccountTaxCreditCalculator calculator;
    private PensionAccountTaxCreditCalculator.PensionAccountRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new PensionAccountTaxCreditCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("납입액 없음: 세액공제 0")
    void noContributions_zeroCreditAmount() {
        var result = calculator.calculate(0L, 0L, 0L, 50_000_000L, ruleSnapshot);

        assertThat(result.totalCreditAmount()).isZero();
    }

    @Test
    @DisplayName("연금저축만 있고 총급여 5500만 이하: 15% 공제율 적용")
    void pensionSavingsOnly_highRate() {
        // 300만 납입, 총급여 5000만 → 300만 × 15% = 450,000
        var result = calculator.calculate(3_000_000L, 0L, 0L, 50_000_000L, ruleSnapshot);

        assertThat(result.applicablePensionSavingsAmount()).isEqualTo(3_000_000L);
        assertThat(result.applicableCombinedAmount()).isEqualTo(3_000_000L);
        assertThat(result.totalApplicableAmount()).isEqualTo(3_000_000L);
        assertThat(result.creditRate()).isEqualTo(0.15);
        assertThat(result.totalCreditAmount()).isEqualTo(450_000L);
    }

    @Test
    @DisplayName("총급여 5500만 초과: 12% 공제율 적용")
    void grossSalaryAboveThreshold_lowRate() {
        // 300만 납입, 총급여 6000만 → 300만 × 12% = 360,000
        var result = calculator.calculate(3_000_000L, 0L, 0L, 60_000_000L, ruleSnapshot);

        assertThat(result.creditRate()).isEqualTo(0.12);
        assertThat(result.totalCreditAmount()).isEqualTo(360_000L);
    }

    @Test
    @DisplayName("연금저축 600만 한도 적용")
    void pensionSavingsCapAt6Million() {
        // 700만 납입 → 한도 600만으로 cap, 600만 × 15% = 900,000
        var result = calculator.calculate(7_000_000L, 0L, 0L, 50_000_000L, ruleSnapshot);

        assertThat(result.applicablePensionSavingsAmount()).isEqualTo(6_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(900_000L);
    }

    @Test
    @DisplayName("연금저축+IRP 합산 900만 한도 적용")
    void combinedCapAt9Million() {
        // 연금저축 600만 + IRP 500만 → 합산한도 900만, 900만 × 15% = 1,350,000
        var result = calculator.calculate(6_000_000L, 5_000_000L, 0L, 50_000_000L, ruleSnapshot);

        assertThat(result.applicablePensionSavingsAmount()).isEqualTo(6_000_000L);
        assertThat(result.applicableCombinedAmount()).isEqualTo(9_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(1_350_000L);
    }

    @Test
    @DisplayName("ISA 만기 전환 추가 한도 적용")
    void isaMaturityTransferAddsToLimit() {
        // 연금저축 900만(→600만), IRP 0, ISA 전환 2000만 → isaAdditional = min(2000만×10%, 300만) = 200만
        // totalApplicable = 600만 + 200만 = 800만, 800만 × 15% = 1,200,000
        var result = calculator.calculate(9_000_000L, 0L, 20_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.applicablePensionSavingsAmount()).isEqualTo(6_000_000L);
        assertThat(result.isaAdditionalAmount()).isEqualTo(2_000_000L);
        assertThat(result.totalApplicableAmount()).isEqualTo(8_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("ISA 만기 전환 추가 한도 300만 상한 적용")
    void isaMaturityTransferCappedAt3Million() {
        // ISA 전환 5000만 → isaAdditional = min(5000만×10%, 300만) = 300만
        var result = calculator.calculate(0L, 0L, 50_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.isaAdditionalAmount()).isEqualTo(3_000_000L);
        assertThat(result.totalApplicableAmount()).isEqualTo(3_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(450_000L);
    }

    @Test
    @DisplayName("연금저축+IRP 한도+ISA 추가 최대 공제: 총급여 5500만 이하")
    void maxCreditHighRate() {
        // 연금저축 600만 + IRP 300만 + ISA 전환 3000만(→추가300만) → total=1200만
        // 1200만 × 15% = 1,800,000
        var result = calculator.calculate(6_000_000L, 3_000_000L, 30_000_000L, 50_000_000L, ruleSnapshot);

        assertThat(result.applicableCombinedAmount()).isEqualTo(9_000_000L);
        assertThat(result.isaAdditionalAmount()).isEqualTo(3_000_000L);
        assertThat(result.totalApplicableAmount()).isEqualTo(12_000_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(1_800_000L);
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule(PensionAccountTaxCreditCalculator.LIMIT_RULE_CODE, RuleCategory.LIMIT,
                    "{\"pensionSavingsLimit\":6000000,\"combinedPensionLimit\":9000000,\"isaAdditionalRate\":0.10,\"isaAdditionalMaxLimit\":3000000}"),
                rule(PensionAccountTaxCreditCalculator.RATE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"grossSalaryThreshold\":55000000,\"highRate\":0.15,\"lowRate\":0.12}"),
                rule(PensionAccountTaxCreditCalculator.TRACE_RULE_CODE, RuleCategory.FORMULA,
                    "{\"traceFields\":[\"pensionSavingsAmount\",\"irpAmount\",\"applicablePensionSavingsAmount\",\"applicableCombinedAmount\",\"isaMaturityTransferAmount\",\"isaAdditionalAmount\",\"totalApplicableAmount\",\"creditRate\",\"pensionAccountTaxCreditAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.PENSION_ACCOUNT);
        rule.setRuleCode(ruleCode);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
