package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.AbstractDeductionPolicy;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.domain.TaxContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 보장성 보험료 세액공제 (소득세법 제59조의4 제1항)
 * - 일반 보장성보험료: 한도 연 1,000,000원, 공제율 12%
 * - 장애인전용보장성보험료: 한도 연 1,000,000원, 공제율 15%
 * - 두 한도는 독립 적용 (각각 별도 최대 세액공제)
 */
@Component
public class InsurancePremiumDeductionPolicy extends AbstractDeductionPolicy {

    public static final String STANDARD_LIMIT_RATE_RULE_CODE  = "INSURANCE_PREMIUM_STANDARD_LIMIT_RATE_2025";
    public static final String DISABILITY_LIMIT_RATE_RULE_CODE = "INSURANCE_PREMIUM_DISABILITY_LIMIT_RATE_2025";
    public static final String AGGREGATE_FORMULA_RULE_CODE     = "INSURANCE_PREMIUM_AGGREGATE_FORMULA_2025";
    public static final String TRACE_RULE_CODE                 = "INSURANCE_PREMIUM_TRACE_2025";

    private static final String DISABILITY_SUBTYPE = "INSURANCE_PREMIUM_DISABILITY";

    // Fallback constants for legacy / unresolved snapshots
    private static final long   DEFAULT_LIMIT_AMOUNT   = 1_000_000L;
    private static final double DEFAULT_STANDARD_RATE  = 0.12;
    private static final double DEFAULT_DISABILITY_RATE = 0.15;

    private final ObjectMapper objectMapper;

    public InsurancePremiumDeductionPolicy(ObjectMapper objectMapper) {
        super(List.of());
        this.objectMapper = objectMapper;
    }

    @Override
    public DeductionType supports() {
        return DeductionType.INSURANCE;
    }

    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        // 보험료는 소득공제가 아니므로 소득공제 기여분은 0
        return 0L;
    }

    @Override
    protected long calculateTaxCredit(TaxContext context, DeductionItem item) {
        InsuranceRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        long capped = Math.min(item.getAmount(), params.limitAmount());
        return Math.round(capped * params.creditRate());
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item, long eligibleAmount, long appliedAmount) {
        InsuranceRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        long capped = Math.min(item.getAmount(), params.limitAmount());
        long credit = Math.round(capped * params.creditRate());
        String typeLabel = DISABILITY_SUBTYPE.equals(item.getSubType())
            ? "장애인전용보장성보험료" : "보장성보험료";
        return String.format(
            "%s 세액공제 적용: 납입액 %,d원, 한도 적용 후 %,d원, 세액공제 %,d원 (공제율 %.0f%%)",
            typeLabel, item.getAmount(), capped, credit, params.creditRate() * 100
        );
    }

    private InsuranceRuleParams resolveParams(RuleSetSnapshot snapshot, String subType) {
        boolean isDisability = DISABILITY_SUBTYPE.equals(subType);
        String ruleCode = isDisability ? DISABILITY_LIMIT_RATE_RULE_CODE : STANDARD_LIMIT_RATE_RULE_CODE;
        double defaultRate = isDisability ? DEFAULT_DISABILITY_RATE : DEFAULT_STANDARD_RATE;

        return snapshot.rulesFor(DeductionType.INSURANCE).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .map(rule -> parseParams(rule, defaultRate))
            .orElse(new InsuranceRuleParams(DEFAULT_LIMIT_AMOUNT, defaultRate));
    }

    private InsuranceRuleParams parseParams(DeductionRule rule, double defaultRate) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
            return new InsuranceRuleParams(
                params.path("limitAmount").asLong(DEFAULT_LIMIT_AMOUNT),
                params.path("creditRate").asDouble(defaultRate)
            );
        } catch (Exception exception) {
            return new InsuranceRuleParams(DEFAULT_LIMIT_AMOUNT, defaultRate);
        }
    }

    private record InsuranceRuleParams(long limitAmount, double creditRate) {
    }
}
