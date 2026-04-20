package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SmeMutualAidDeductionCalculator {

    public static final String TIERED_LIMIT_RULE_CODE = "SME_MUTUAL_AID_TIERED_LIMIT_2025";
    public static final String RATE_RULE_CODE         = "SME_MUTUAL_AID_RATE_2025";
    public static final String TRACE_RULE_CODE        = "SME_MUTUAL_AID_TRACE_2025";

    private final ObjectMapper objectMapper;

    public SmeMutualAidDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SmeMutualAidRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule tieredLimitRule = findRule(ruleSetSnapshot, TIERED_LIMIT_RULE_CODE);
        DeductionRule rateRule        = findRule(ruleSetSnapshot, RATE_RULE_CODE);

        JsonNode tieredLimitParameters = readParameters(tieredLimitRule);
        JsonNode rateParameters        = readParameters(rateRule);

        double deductionRate        = rateParameters.path("deductionRate").asDouble(1.00);
        List<BusinessIncomeTier> tiers = readTiers(tieredLimitParameters);

        return new SmeMutualAidRuleSnapshot(deductionRate, tiers);
    }

    public SmeMutualAidCalculation calculate(
        long smeMutualAidContributionAmount,
        long smeMutualAidIncomeBasisAmount,
        SmeMutualAidRuleSnapshot ruleSnapshot
    ) {
        long contributionAmount         = Math.max(0L, smeMutualAidContributionAmount);
        long incomeBasisAmount          = Math.max(0L, smeMutualAidIncomeBasisAmount);
        long deductionBeforeLimitAmount = (long) (contributionAmount * ruleSnapshot.deductionRate());
        long appliedTierLimit           = resolveTierLimit(incomeBasisAmount, ruleSnapshot.tiers());
        long smeMutualAidDeductionAmount = Math.max(
            0L,
            Math.min(deductionBeforeLimitAmount, appliedTierLimit)
        );

        return new SmeMutualAidCalculation(
            contributionAmount,
            incomeBasisAmount,
            deductionBeforeLimitAmount,
            appliedTierLimit,
            smeMutualAidDeductionAmount
        );
    }

    private long resolveTierLimit(long businessIncomeAmount, List<BusinessIncomeTier> tiers) {
        for (BusinessIncomeTier tier : tiers) {
            if (tier.maxBusinessIncomeAmount() == null || businessIncomeAmount <= tier.maxBusinessIncomeAmount()) {
                return tier.annualDeductionLimit();
            }
        }
        return tiers.isEmpty() ? 2_000_000L : tiers.getLast().annualDeductionLimit();
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.SME_MUTUAL_AID).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required SME mutual aid deduction ruleCode: " + ruleCode
            ));
    }

    private JsonNode readParameters(DeductionRule rule) {
        try {
            return objectMapper.readTree(rule.getParameterJsonb());
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Failed to parse parameterJsonb for ruleCode " + rule.getRuleCode() + ".",
                exception
            );
        }
    }

    private List<BusinessIncomeTier> readTiers(JsonNode parameters) {
        List<BusinessIncomeTier> tiers = new ArrayList<>();
        JsonNode tiersNode = parameters.get("tiers");
        if (tiersNode == null || !tiersNode.isArray()) {
            return tiers;
        }
        for (JsonNode tierNode : tiersNode) {
            JsonNode maxNode = tierNode.get("maxBusinessIncomeAmount");
            Long maxBusinessIncomeAmount = (maxNode == null || maxNode.isNull()) ? null : maxNode.asLong();
            long annualDeductionLimit = tierNode.path("annualDeductionLimit").asLong(2_000_000L);
            tiers.add(new BusinessIncomeTier(maxBusinessIncomeAmount, annualDeductionLimit));
        }
        return tiers;
    }

    public record SmeMutualAidRuleSnapshot(
        double deductionRate,
        List<BusinessIncomeTier> tiers
    ) {
        public SmeMutualAidRuleSnapshot {
            tiers = List.copyOf(tiers);
        }
    }

    public record SmeMutualAidCalculation(
        long smeMutualAidContributionAmount,
        long smeMutualAidIncomeBasisAmount,
        long deductionBeforeLimitAmount,
        long smeMutualAidAppliedTierLimit,
        long smeMutualAidDeductionAmount
    ) {
    }

    public record BusinessIncomeTier(
        Long maxBusinessIncomeAmount,
        long annualDeductionLimit
    ) {
    }
}
