package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTaxCalculationServiceTest {

    @Test
    @DisplayName("적용 가능한 공제만 반영해 예상 환급액을 계산한다")
    void calculateRefund() {
        DefaultTaxCalculationService service = new DefaultTaxCalculationService();
        TaxCalculationCommand command = new TaxCalculationCommand(
            10_000_000L,
            0L,
            10_000_000L,
            0L,
            1_000_000L,
            List.of(
                new DeductionDecision(UUID.randomUUID(), DeductionType.MEDICAL_EXPENSE, true, 1_000_000L, 1_000_000L, 1_000_000L, 0L, List.of("applied")),
                new DeductionDecision(UUID.randomUUID(), DeductionType.DONATION, false, 500_000L, 0L, 0L, 0L, List.of("rejected"))
            )
        );

        TaxCalculationOutcome outcome = service.calculate(command);

        assertThat(outcome.earnedIncomeDeductionAmount()).isEqualTo(5_500_000L);
        assertThat(outcome.earnedIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.totalIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.totalDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(3_500_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(210_000L);
        assertThat(outcome.expectedRefundAmount()).isEqualTo(790_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains("EARNED_INCOME_DEDUCTION_BRACKETS"));
    }
}
