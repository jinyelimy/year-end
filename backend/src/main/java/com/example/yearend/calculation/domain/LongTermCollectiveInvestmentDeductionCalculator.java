package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class LongTermCollectiveInvestmentDeductionCalculator {

    public static final String LIMIT_RULE_CODE = "LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "LONG_TERM_COLLECTIVE_INVESTMENT_RATE_2025";
    public static final String TRACE_RULE_CODE = "LONG_TERM_COLLECTIVE_INVESTMENT_TRACE_2025";

    private final ObjectMapper objectMapper;

    public LongTermCollectiveInvestmentDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LongTermCollectiveInvestmentRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);

        JsonNode limitParameters = readParameters(limitRule);
        JsonNode rateParameters  = readParameters(rateRule);

        long annualDeductionLimit = limitParameters.path("annualDeductionLimit").asLong(2_400_000L);
        double deductionRate      = rateParameters.path("deductionRate").asDouble(0.40);

        return new LongTermCollectiveInvestmentRuleSnapshot(annualDeductionLimit, deductionRate);
    }

    public LongTermCollectiveInvestmentCalculation calculate(
        long longTermCollectiveInvestmentContributionAmount,
        LongTermCollectiveInvestmentRuleSnapshot ruleSnapshot
    ) {
        long contributionAmount         = Math.max(0L, longTermCollectiveInvestmentContributionAmount);
        long deductionBeforeLimitAmount = (long)(contributionAmount * ruleSnapshot.deductionRate());
        long longTermCollectiveInvestmentDeductionAmount =
            Math.min(deductionBeforeLimitAmount, ruleSnapshot.annualDeductionLimit());

        return new LongTermCollectiveInvestmentCalculation(
            contributionAmount,
            deductionBeforeLimitAmount,
            longTermCollectiveInvestmentDeductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required long-term collective investment deduction ruleCode: " + ruleCode
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

    public record LongTermCollectiveInvestmentRuleSnapshot(
        long annualDeductionLimit,
        double deductionRate
    ) {
    }

    public record LongTermCollectiveInvestmentCalculation(
        long longTermCollectiveInvestmentContributionAmount,
        long deductionBeforeLimitAmount,
        long longTermCollectiveInvestmentDeductionAmount
    ) {
    }
}
