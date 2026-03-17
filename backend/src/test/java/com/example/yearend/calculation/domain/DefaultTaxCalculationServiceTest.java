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
            1_000_000L,
            List.of(
                new DeductionDecision(UUID.randomUUID(), DeductionType.MEDICAL_EXPENSE, true, 1_000_000L, 1_000_000L, 1_000_000L, List.of("applied")),
                new DeductionDecision(UUID.randomUUID(), DeductionType.DONATION, false, 500_000L, 0L, 0L, List.of("rejected"))
            )
        );

        TaxCalculationOutcome outcome = service.calculate(command);

        assertThat(outcome.totalDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(9_000_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(540_000L);
        assertThat(outcome.expectedRefundAmount()).isEqualTo(460_000L);
    }
}
