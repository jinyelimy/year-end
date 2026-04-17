package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.DependentIncomeEligibilityChecker;
import com.example.yearend.deduction.infrastructure.EducationExpenseDeductionPolicy;
import com.example.yearend.deduction.infrastructure.EducationInstitutionChecker;
import com.example.yearend.taxsession.domain.Dependent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EducationExpenseDeductionPolicyTest {

    private EducationExpenseDeductionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new EducationExpenseDeductionPolicy(
            new DependentIncomeEligibilityChecker(),
            new EducationInstitutionChecker(),
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("대학교 교육비(UNIVERSITY): 한도(900만원) 내 15% 세액공제, 소득공제 기여 0")
    void universityWithinLimit_taxCreditApplied() {
        // 납입액 5M < 한도 9M → 세액공제 = 5M × 15% = 750,000
        TaxContext context = buildContext();
        DeductionItem item = buildItem("UNIVERSITY", 5_000_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(750_000L);
    }

    @Test
    @DisplayName("대학교 교육비(UNIVERSITY): 납입액이 한도(900만원) 초과 시 한도 적용")
    void universityExceedsLimit_limitApplied() {
        // 납입액 12M > 한도 9M → 세액공제 = 9M × 15% = 1,350,000
        TaxContext context = buildContext();
        DeductionItem item = buildItem("UNIVERSITY", 12_000_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(1_350_000L);
    }

    @Test
    @DisplayName("본인 교육비(SELF_EDUCATION): 한도없음, 전액 15% 세액공제")
    void selfEducation_noLimit() {
        // 납입액 15M, 한도없음 → 세액공제 = 15M × 15% = 2,250,000
        TaxContext context = new TaxContext(2025, 50_000_000L, 0L, List.of(), List.of());
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.EDUCATION_EXPENSE);
        item.setSubType("SELF_EDUCATION");
        item.setAmount(15_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(2_250_000L);
    }

    @Test
    @DisplayName("취학전아동(PRESCHOOL): 300만원 한도 내 15% 세액공제")
    void preschool_limitApplied() {
        // 납입액 3.5M > 한도 3M → 세액공제 = 3M × 15% = 450,000
        TaxContext context = buildContext();
        DeductionItem item = buildItem("PRESCHOOL", 3_500_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(450_000L);
    }

    @Test
    @DisplayName("초중고(SCHOOL): 300만원 한도 내 15% 세액공제")
    void school_withinLimit() {
        // 납입액 2M < 한도 3M → 세액공제 = 2M × 15% = 300,000
        TaxContext context = buildContext();
        DeductionItem item = buildItem("SCHOOL", 2_000_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("교복구입비(UNIFORM): 50만원 한도 내 15% 세액공제")
    void uniform_limitApplied() {
        // 납입액 600,000 > 한도 500,000 → 세액공제 = 500,000 × 15% = 75,000
        TaxContext context = buildContext();
        DeductionItem item = buildItem("UNIFORM", 600_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(75_000L);
    }

    @Test
    @DisplayName("미지원 subType이면 공제 거부")
    void unsupportedSubType_rejected() {
        TaxContext context = buildContext();
        DeductionItem item = buildItem("FOREIGN_LANGUAGE_CAMP", 1_000_000L, context);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.taxCreditContribution()).isZero();
    }

    private TaxContext buildContext() {
        Dependent dependent = new Dependent();
        dependent.setId(UUID.randomUUID());
        dependent.setAnnualIncomeAmount(500_000L);

        return new TaxContext(2025, 50_000_000L, 0L, List.of(dependent), List.of());
    }

    private DeductionItem buildItem(String subType, long amount, TaxContext context) {
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.EDUCATION_EXPENSE);
        item.setSubType(subType);
        item.setAmount(amount);

        if (!context.dependents().isEmpty() && !"SELF_EDUCATION".equals(subType)) {
            item.setDependent(context.dependents().get(0));
        }
        return item;
    }
}
