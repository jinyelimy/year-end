package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.MedicalExpenseDeductionPolicy;
import com.example.yearend.deduction.infrastructure.MedicalExpenseThresholdChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalExpenseDeductionPolicyTest {

    @Test
    @DisplayName("의료비가 총급여 3퍼센트를 초과하면 초과분만 공제 대상이 된다")
    void evaluateMedicalExpense() {
        MedicalExpenseDeductionPolicy policy = new MedicalExpenseDeductionPolicy(new MedicalExpenseThresholdChecker());
        TaxContext context = new TaxContext(2025, 50_000_000L, 1_500_000L, List.of(), List.of());

        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.MEDICAL_EXPENSE);
        item.setAmount(2_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.eligibleAmount()).isEqualTo(500_000L);
        assertThat(decision.appliedAmount()).isEqualTo(500_000L);
    }
}
