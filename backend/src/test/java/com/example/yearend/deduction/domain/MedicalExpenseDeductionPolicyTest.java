package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.MedicalExpenseDeductionPolicy;
import com.example.yearend.deduction.infrastructure.MedicalExpenseThresholdChecker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalExpenseDeductionPolicyTest {

    private MedicalExpenseDeductionPolicy policy;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        MedicalExpenseThresholdChecker checker = new MedicalExpenseThresholdChecker(objectMapper);
        policy = new MedicalExpenseDeductionPolicy(objectMapper, checker);
    }

    @Test
    @DisplayName("일반 의료비(MEDICAL_TOTAL): 총급여 3% 초과분에 15% 세액공제, 소득공제 기여 0")
    void generalMedicalExpense_taxCreditApplied() {
        // 총급여 50M, 임계값 1.5M, 의료비 2M → 초과분 0.5M × 15% = 75,000원
        TaxContext context = new TaxContext(2025, 50_000_000L, 1_500_000L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_TOTAL", 2_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();            // 소득공제 기여 없음
        assertThat(decision.taxCreditContribution()).isEqualTo(75_000L); // 500,000 × 0.15
    }

    @Test
    @DisplayName("일반 의료비(MEDICAL_GENERAL): 공제대상이 700만원 한도를 초과하면 한도 적용")
    void generalMedicalExpense_limitApplied() {
        // 총급여 100M, 임계값 3M, 의료비 15M → 초과분 12M → 한도 7M 적용 → 7M × 15% = 1,050,000원
        TaxContext context = new TaxContext(2025, 100_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_GENERAL", 15_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(1_050_000L); // 7,000,000 × 0.15
    }

    @Test
    @DisplayName("난임시술비(MEDICAL_INFERTILITY): 30% 세액공제, 한도없음")
    void infertilityMedicalExpense_higherRate() {
        // 총급여 60M, 임계값 1.8M, 의료비 4M → 초과분 2.2M × 30% = 660,000원
        TaxContext context = new TaxContext(2025, 60_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_INFERTILITY", 4_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(660_000L); // 2,200,000 × 0.30
    }

    @Test
    @DisplayName("미숙아/선천성이상아(MEDICAL_PREMATURE): 20% 세액공제, 한도없음")
    void prematureMedicalExpense_twentyPercent() {
        // 총급여 40M, 임계값 1.2M, 의료비 3.2M → 초과분 2M × 20% = 400,000원
        TaxContext context = new TaxContext(2025, 40_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_PREMATURE", 3_200_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(400_000L); // 2,000,000 × 0.20
    }

    @Test
    @DisplayName("장애인 의료비(MEDICAL_DISABILITY): 15% 세액공제, 한도없음 (고액도 전액 적용)")
    void disabilityMedicalExpense_noLimit() {
        // 총급여 50M, 임계값 1.5M, 의료비 20M → 초과분 18.5M × 15% = 2,775,000원 (한도없음)
        TaxContext context = new TaxContext(2025, 50_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_DISABILITY", 20_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(2_775_000L); // 18,500,000 × 0.15
    }

    @Test
    @DisplayName("의료비가 총급여 3% 이하이면 공제 거부")
    void belowThreshold_rejected() {
        // 총급여 50M, 임계값 1.5M, 의료비 1M (< 임계값)
        TaxContext context = new TaxContext(2025, 50_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_TOTAL", 1_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isFalse();
        assertThat(decision.taxCreditContribution()).isZero();
    }

    @Test
    @DisplayName("65세이상(MEDICAL_SENIOR): 15% 세액공제, 한도없음")
    void seniorMedicalExpense_noLimit() {
        // 총급여 50M, 임계값 1.5M, 의료비 10M → 초과분 8.5M × 15% = 1,275,000원
        TaxContext context = new TaxContext(2025, 50_000_000L, 0L, List.of(), List.of());
        DeductionItem item = buildItem(DeductionType.MEDICAL_EXPENSE, "MEDICAL_SENIOR", 10_000_000L);

        DeductionDecision decision = policy.evaluate(context, item);

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(1_275_000L); // 8,500,000 × 0.15
    }

    private DeductionItem buildItem(DeductionType type, String subType, long amount) {
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(type);
        item.setSubType(subType);
        item.setAmount(amount);
        return item;
    }
}
