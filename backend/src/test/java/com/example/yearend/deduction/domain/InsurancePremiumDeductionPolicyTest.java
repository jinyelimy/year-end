package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.InsurancePremiumDeductionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InsurancePremiumDeductionPolicyTest {

    private final InsurancePremiumDeductionPolicy policy = new InsurancePremiumDeductionPolicy();
    private final TaxContext context = new TaxContext(2025, 50_000_000L, 1_500_000L, List.of(), List.of());

    @Test
    @DisplayName("보험료 납입액이 한도(100만원) 미만이면 납입액의 12%가 세액공제된다")
    void creditBelowLimit() {
        DeductionDecision decision = policy.evaluate(context, item(500_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isEqualTo(0L);          // 소득공제 없음
        assertThat(decision.taxCreditContribution()).isEqualTo(60_000L); // 500,000 × 12%
    }

    @Test
    @DisplayName("보험료 납입액이 한도(100만원)와 정확히 같으면 최대 세액공제 12만원이 적용된다")
    void creditAtLimit() {
        DeductionDecision decision = policy.evaluate(context, item(1_000_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.taxCreditContribution()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("보험료 납입액이 한도(100만원)를 초과하면 100만원 기준으로 세액공제 12만원이 적용된다")
    void creditCappedAtLimit() {
        DeductionDecision decision = policy.evaluate(context, item(2_000_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isEqualTo(0L);
        assertThat(decision.taxCreditContribution()).isEqualTo(120_000L); // 한도 초과분 무시
    }

    @Test
    @DisplayName("보험료 공제는 소득공제(appliedAmount)에 기여하지 않는다")
    void noIncomeDeductionContribution() {
        DeductionDecision decision = policy.evaluate(context, item(800_000L));

        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.eligibleAmount()).isZero();
    }

    private DeductionItem item(long amount) {
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(DeductionType.INSURANCE);
        item.setAmount(amount);
        return item;
    }
}
