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
public class VentureInvestmentDeductionCalculator {

    public static final String RATE_DIRECT_TIERED_RULE_CODE = "VENTURE_INVESTMENT_RATE_DIRECT_TIERED_2025";
    public static final String RATE_FUND_RULE_CODE          = "VENTURE_INVESTMENT_RATE_FUND_2025";
    public static final String INCOME_LIMIT_RULE_CODE       = "VENTURE_INVESTMENT_INCOME_LIMIT_2025";
    public static final String TRACE_RULE_CODE              = "VENTURE_INVESTMENT_TRACE_2025";

    private final ObjectMapper objectMapper;

    public VentureInvestmentDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VentureInvestmentRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule directTieredRule = findRule(ruleSetSnapshot, RATE_DIRECT_TIERED_RULE_CODE);
        DeductionRule fundRule         = findRule(ruleSetSnapshot, RATE_FUND_RULE_CODE);
        DeductionRule incomeLimitRule  = findRule(ruleSetSnapshot, INCOME_LIMIT_RULE_CODE);
        DeductionRule traceRule        = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode directTieredParameters = readParameters(directTieredRule);
        JsonNode fundParameters         = readParameters(fundRule);
        JsonNode incomeLimitParameters  = readParameters(incomeLimitRule);
        JsonNode traceParameters        = readParameters(traceRule);

        List<ContributionTier> tiers   = readTiers(directTieredParameters);
        double fundRate                = fundParameters.path("deductionRate").asDouble(0.10);
        double incomeLimitRate         = incomeLimitParameters.path("comprehensiveIncomeLimitRate").asDouble(0.50);
        List<String> traceFields       = readTraceFields(traceParameters);

        return new VentureInvestmentRuleSnapshot(tiers, fundRate, incomeLimitRate, traceFields);
    }

    public VentureInvestmentCalculation calculate(
        long ventureDirectInvestmentAmount,
        long ventureFundInvestmentAmount,
        long comprehensiveIncomeAmount,
        VentureInvestmentRuleSnapshot ruleSnapshot
    ) {
        long directAmount = Math.max(0L, ventureDirectInvestmentAmount);
        long fundAmount   = Math.max(0L, ventureFundInvestmentAmount);
        long incomeAmount = Math.max(0L, comprehensiveIncomeAmount);

        long directDeductionAmount = computeDirectDeduction(directAmount, ruleSnapshot.tiers());
        long fundDeductionAmount   = Math.round(fundAmount * ruleSnapshot.fundRate());
        long beforeIncomeCapAmount = directDeductionAmount + fundDeductionAmount;
        long incomeCapAmount       = Math.round(incomeAmount * ruleSnapshot.incomeLimitRate());
        long ventureInvestmentDeductionAmount = Math.max(
            0L,
            Math.min(beforeIncomeCapAmount, incomeCapAmount)
        );

        return new VentureInvestmentCalculation(
            directAmount,
            fundAmount,
            directDeductionAmount,
            fundDeductionAmount,
            beforeIncomeCapAmount,
            ventureInvestmentDeductionAmount
        );
    }

    private long computeDirectDeduction(long directAmount, List<ContributionTier> tiers) {
        if (directAmount <= 0L || tiers.isEmpty()) {
            return 0L;
        }
        long remaining = directAmount;
        long previousTierMax = 0L;
        long accumulated = 0L;
        for (ContributionTier tier : tiers) {
            if (remaining <= 0L) {
                break;
            }
            long tierSize;
            if (tier.maxContributionAmount() == null) {
                tierSize = remaining;
            } else {
                tierSize = Math.max(0L, tier.maxContributionAmount() - previousTierMax);
            }
            long portion = Math.min(remaining, tierSize);
            accumulated += Math.round(portion * tier.deductionRate());
            remaining -= portion;
            if (tier.maxContributionAmount() != null) {
                previousTierMax = tier.maxContributionAmount();
            }
        }
        return accumulated;
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.VENTURE_INVESTMENT).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required venture investment deduction ruleCode: " + ruleCode
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

    private List<ContributionTier> readTiers(JsonNode parameters) {
        List<ContributionTier> tiers = new ArrayList<>();
        JsonNode tiersNode = parameters.get("tiers");
        if (tiersNode == null || !tiersNode.isArray()) {
            return tiers;
        }
        for (JsonNode tierNode : tiersNode) {
            JsonNode maxNode = tierNode.get("maxContributionAmount");
            Long maxContributionAmount = (maxNode == null || maxNode.isNull()) ? null : maxNode.asLong();
            double deductionRate = tierNode.path("deductionRate").asDouble(0.0);
            tiers.add(new ContributionTier(maxContributionAmount, deductionRate));
        }
        return tiers;
    }

    private List<String> readTraceFields(JsonNode parameters) {
        List<String> fields = new ArrayList<>();
        JsonNode traceFieldsNode = parameters.get("traceFields");
        if (traceFieldsNode == null || !traceFieldsNode.isArray()) {
            return fields;
        }
        for (JsonNode fieldNode : traceFieldsNode) {
            fields.add(fieldNode.asText());
        }
        return fields;
    }

    public record VentureInvestmentRuleSnapshot(
        List<ContributionTier> tiers,
        double fundRate,
        double incomeLimitRate,
        List<String> traceFields
    ) {
        public VentureInvestmentRuleSnapshot {
            tiers = List.copyOf(tiers);
            traceFields = List.copyOf(traceFields);
        }
    }

    public record VentureInvestmentCalculation(
        long ventureDirectInvestmentAmount,
        long ventureFundInvestmentAmount,
        long ventureDirectDeductionAmount,
        long ventureFundDeductionAmount,
        long ventureDeductionBeforeIncomeCapAmount,
        long ventureInvestmentDeductionAmount
    ) {
    }

    public record ContributionTier(
        Long maxContributionAmount,
        double deductionRate
    ) {
    }
}
