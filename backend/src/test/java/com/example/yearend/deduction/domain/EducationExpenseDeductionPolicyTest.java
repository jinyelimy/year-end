package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.DependentIncomeEligibilityChecker;
import com.example.yearend.deduction.infrastructure.EducationExpenseDeductionPolicy;
import com.example.yearend.deduction.infrastructure.EducationInstitutionChecker;
import com.example.yearend.taxsession.domain.Dependent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EducationExpenseDeductionPolicyTest {

    @Test
    @DisplayName("대학교 교육비는 한도 내에서 공제된다")
    void evaluateEducationExpense() {
        EducationExpenseDeductionPolicy policy = new EducationExpenseDeductionPolicy(
            new DependentIncomeEligibilityChecker(),
            new EducationInstitutionChecker()
        );

        Dependent dependent = new Dependent();
        dependent.setId(UUID.randomUUID());
        dependent.setAnnualIncomeAmount(500_000L);

        TaxContext context = new TaxContext(2025, 50_000_000L, 1_500_000L, List.of(dependent), List.of());

        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.EDUCATION_EXPENSE);
        item.setDependent(dependent);
        item.setSubType("UNIVERSITY");
        item.setAmount(10_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.eligibleAmount()).isEqualTo(10_000_000L);
        assertThat(decision.appliedAmount()).isEqualTo(9_000_000L);
    }

    @Test
    @DisplayName("supports self education import subtype without applying a cap")
    void evaluateSelfEducationSubtype() {
        EducationExpenseDeductionPolicy policy = new EducationExpenseDeductionPolicy(
            new DependentIncomeEligibilityChecker(),
            new EducationInstitutionChecker()
        );

        TaxContext context = new TaxContext(2025, 50_000_000L, 1_500_000L, List.of(), List.of());

        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.EDUCATION_EXPENSE);
        item.setSubType("SELF_EDUCATION");
        item.setAmount(2_020_255L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.eligibleAmount()).isEqualTo(2_020_255L);
        assertThat(decision.appliedAmount()).isEqualTo(2_020_255L);
    }
}
