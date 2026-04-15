package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTaxCalculationServiceTest {

    @Test
    @DisplayName("적용 가능한 공제만 반영해 예상 환급액을 계산한다")
    void calculateRefund() {
        DefaultTaxCalculationService service = new DefaultTaxCalculationService(
            new EarnedIncomeDeductionCalculator(new ObjectMapper()),
            new PersonalDeductionCalculator(new ObjectMapper()),
            new IncomeTaxRateTableCalculator(new ObjectMapper()),
            new EarnedIncomeTaxCreditCalculator(new ObjectMapper())
        );
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            10_000_000L,
            0L,
            10_000_000L,
            0L,
            1_000_000L,
            List.of(),
            Map.of(),
            List.of(
                new DeductionDecision(UUID.randomUUID(), DeductionType.MEDICAL_EXPENSE, true, 1_000_000L, 1_000_000L, 1_000_000L, 0L, List.of("applied")),
                new DeductionDecision(UUID.randomUUID(), DeductionType.DONATION, false, 500_000L, 0L, 0L, 0L, List.of("rejected"))
            ),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = service.calculate(command);

        assertThat(outcome.earnedIncomeDeductionAmount()).isEqualTo(5_500_000L);
        assertThat(outcome.earnedIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.totalIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.totalDeductionAmount()).isEqualTo(2_500_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(2_000_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(120_000L);
        assertThat(outcome.earnedIncomeTaxCreditAmount()).isEqualTo(66_000L);
        assertThat(outcome.otherTaxCreditAmount()).isZero();
        assertThat(outcome.taxCreditAmount()).isEqualTo(66_000L);
        assertThat(outcome.finalTaxAmount()).isEqualTo(54_000L);
        assertThat(outcome.expectedRefundAmount()).isEqualTo(946_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains("EARNED_INCOME_DEDUCTION_BRACKETS"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("earnedIncomeTaxCreditAmount = 66000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("PERSONAL_BASIC_DEDUCTION_AMOUNT_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("personalDeductionAmount = 1500000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("INCOME_TAX_BASIC_BRACKETS_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("effectiveFrom = 2025-01-01"));
    }

    @Test
    @DisplayName("applies the income tax rate table from the resolved rule snapshot")
    void appliesIncomeTaxRateTable() {
        DefaultTaxCalculationService service = new DefaultTaxCalculationService(
            new EarnedIncomeDeductionCalculator(new ObjectMapper()),
            new PersonalDeductionCalculator(new ObjectMapper()),
            new IncomeTaxRateTableCalculator(new ObjectMapper()),
            new EarnedIncomeTaxCreditCalculator(new ObjectMapper())
        );
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            30_000_000L,
            5_000_000L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = service.calculate(command);

        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(28_500_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(3_015_000L);
        assertThat(outcome.earnedIncomeTaxCreditAmount()).isZero();
        assertThat(outcome.finalTaxAmount()).isEqualTo(3_015_000L);
        assertThat(outcome.expectedRefundAmount()).isEqualTo(1_985_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxAppliedBracket = 2"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxRate = 0.15"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxQuickDeductionAmount = 1260000"));
    }
}
