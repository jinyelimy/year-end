package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class HousingSavingsDeductionCalculator {

    public static final String LIMIT_RULE_CODE = "HOUSING_SAVINGS_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "HOUSING_SAVINGS_RATE_2025";
    public static final String TRACE_RULE_CODE = "HOUSING_SAVINGS_TRACE_2025";

    private final ObjectMapper objectMapper;

    public HousingSavingsDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HousingSavingsRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);

        JsonNode limitParameters = readParameters(limitRule);
        JsonNode rateParameters  = readParameters(rateRule);

        long annualDeductionLimit = limitParameters.path("annualDeductionLimit").asLong(3_000_000L);
        double deductionRate      = rateParameters.path("deductionRate").asDouble(0.40);

        return new HousingSavingsRuleSnapshot(annualDeductionLimit, deductionRate);
    }

    public HousingSavingsCalculation calculate(
        long housingSavingsContributionAmount,
        HousingSavingsRuleSnapshot ruleSnapshot
    ) {
        long contributionAmount         = Math.max(0L, housingSavingsContributionAmount);
        long deductionBeforeLimitAmount = (long)(contributionAmount * ruleSnapshot.deductionRate());
        long housingSavingsDeductionAmount = Math.min(deductionBeforeLimitAmount, ruleSnapshot.annualDeductionLimit());

        return new HousingSavingsCalculation(
            contributionAmount,
            deductionBeforeLimitAmount,
            housingSavingsDeductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.HOUSING_SAVINGS).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required housing savings deduction ruleCode: " + ruleCode
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

    public record HousingSavingsRuleSnapshot(
        long annualDeductionLimit,
        double deductionRate
    ) {
    }

    public record HousingSavingsCalculation(
        long housingSavingsContributionAmount,
        long deductionBeforeLimitAmount,
        long housingSavingsDeductionAmount
    ) {
    }
}
