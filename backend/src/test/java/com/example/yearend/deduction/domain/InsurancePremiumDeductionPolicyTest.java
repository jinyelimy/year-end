package com.example.yearend.deduction.domain;

import com.example.yearend.deduction.infrastructure.InsurancePremiumDeductionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InsurancePremiumDeductionPolicyTest {

    private final InsurancePremiumDeductionPolicy policy =
        new InsurancePremiumDeductionPolicy(new ObjectMapper());

    private final TaxContext ruleBasedContext = new TaxContext(
        2025,
        50_000_000L,
        1_500_000L,
        List.of(),
        List.of(),
        Map.of(),
        insuranceRuleSetSnapshot()
    );

    @Test
    @DisplayName("보험료 납입액이 한도(100만원) 미만이면 납입액의 12%가 세액공제된다")
    void creditBelowLimit() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, standardItem(500_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isEqualTo(0L);           // 소득공제 없음
        assertThat(decision.taxCreditContribution()).isEqualTo(60_000L); // 500,000 × 12%
    }

    @Test
    @DisplayName("보험료 납입액이 한도(100만원)와 정확히 같으면 최대 세액공제 12만원이 적용된다")
    void creditAtLimit() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, standardItem(1_000_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.taxCreditContribution()).isEqualTo(120_000L);
    }

    @Test
    @DisplayName("보험료 납입액이 한도(100만원)를 초과하면 100만원 기준으로 세액공제 12만원이 적용된다")
    void creditCappedAtLimit() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, standardItem(2_000_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isEqualTo(0L);
        assertThat(decision.taxCreditContribution()).isEqualTo(120_000L); // 한도 초과분 무시
    }

    @Test
    @DisplayName("보험료 공제는 소득공제(appliedAmount)에 기여하지 않는다")
    void noIncomeDeductionContribution() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, standardItem(800_000L));

        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.eligibleAmount()).isZero();
    }

    @Test
    @DisplayName("장애인전용보장성보험료는 납입액의 15%가 세액공제된다")
    void disabilityInsuranceCreditBelowLimit() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, disabilityItem(500_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.appliedAmount()).isZero();
        assertThat(decision.taxCreditContribution()).isEqualTo(75_000L); // 500,000 × 15%
    }

    @Test
    @DisplayName("장애인전용보장성보험료가 한도(100만원)를 초과하면 15만원(100만원 × 15%)이 최대 세액공제된다")
    void disabilityInsuranceCreditCappedAtLimit() {
        DeductionDecision decision = policy.evaluate(ruleBasedContext, disabilityItem(2_000_000L));

        assertThat(decision.eligible()).isTrue();
        assertThat(decision.taxCreditContribution()).isEqualTo(150_000L); // 1,000,000 × 15%
    }

    @Test
    @DisplayName("일반 보장성보험과 장애인전용 보험의 한도는 독립 적용된다")
    void standardAndDisabilityLimitsAreIndependent() {
        DeductionDecision standard  = policy.evaluate(ruleBasedContext, standardItem(1_000_000L));
        DeductionDecision disability = policy.evaluate(ruleBasedContext, disabilityItem(1_000_000L));

        // 각각 독립 한도 적용
        assertThat(standard.taxCreditContribution()).isEqualTo(120_000L);
        assertThat(disability.taxCreditContribution()).isEqualTo(150_000L);
    }

    // ---- helpers ----

    private DeductionItem standardItem(long amount) {
        return item(amount, "INSURANCE_PREMIUM");
    }

    private DeductionItem disabilityItem(long amount) {
        return item(amount, "INSURANCE_PREMIUM_DISABILITY");
    }

    private DeductionItem item(long amount, String subType) {
        DeductionItem deductionItem = new DeductionItem();
        deductionItem.setId(UUID.randomUUID());
        deductionItem.setDeductionType(DeductionType.INSURANCE);
        deductionItem.setAmount(amount);
        deductionItem.setSubType(subType);
        return deductionItem;
    }

    private static RuleSetSnapshot insuranceRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@insurance-test",
            "2025.01",
            "insurance-test-hash",
            true,
            List.of(
                rule(
                    InsurancePremiumDeductionPolicy.STANDARD_LIMIT_RATE_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":1000000,\"creditRate\":0.12,\"maxCredit\":120000}"
                ),
                rule(
                    InsurancePremiumDeductionPolicy.DISABILITY_LIMIT_RATE_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":1000000,\"creditRate\":0.15,\"maxCredit\":150000}"
                ),
                rule(
                    InsurancePremiumDeductionPolicy.AGGREGATE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"formula\":\"standardCredit + disabilityCredit\"}"
                ),
                rule(
                    InsurancePremiumDeductionPolicy.TRACE_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"traceFields\":[\"subType\",\"amount\",\"limitAmount\",\"creditRate\",\"taxCreditContribution\"]}"
                )
            )
        );
    }

    private static DeductionRule rule(String ruleCode, RuleCategory ruleCategory, String parameterJsonb) {
        DeductionRule r = new DeductionRule();
        r.setTaxYear(2025);
        r.setDeductionType(DeductionType.INSURANCE);
        r.setRuleCode(ruleCode);
        r.setRuleName(ruleCode);
        r.setRuleVersion(1);
        r.setRuleCategory(ruleCategory);
        r.setParameterJsonb(parameterJsonb);
        r.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        r.setEffectiveTo(LocalDate.of(2025, 12, 31));
        r.setActive(true);
        return r;
    }
}
