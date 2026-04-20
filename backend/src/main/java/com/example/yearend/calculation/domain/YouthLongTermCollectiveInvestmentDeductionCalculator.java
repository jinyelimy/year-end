package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class YouthLongTermCollectiveInvestmentDeductionCalculator {

    public static final String LIMIT_RULE_CODE = "YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_RATE_2025";
    public static final String TRACE_RULE_CODE = "YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_TRACE_2025";

    private final ObjectMapper objectMapper;

    public YouthLongTermCollectiveInvestmentDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public YouthLongTermCollectiveInvestmentRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);

        JsonNode limitParameters = readParameters(limitRule);
        JsonNode rateParameters  = readParameters(rateRule);

        long annualDeductionLimit = limitParameters.path("annualDeductionLimit").asLong(2_400_000L);
        double deductionRate      = rateParameters.path("deductionRate").asDouble(0.40);

        return new YouthLongTermCollectiveInvestmentRuleSnapshot(annualDeductionLimit, deductionRate);
    }

    public YouthLongTermCollectiveInvestmentCalculation calculate(
        long youthLongTermCollectiveInvestmentContributionAmount,
        YouthLongTermCollectiveInvestmentRuleSnapshot ruleSnapshot
    ) {
        long contributionAmount         = Math.max(0L, youthLongTermCollectiveInvestmentContributionAmount);
        long deductionBeforeLimitAmount = (long)(contributionAmount * ruleSnapshot.deductionRate());
        long youthLongTermCollectiveInvestmentDeductionAmount =
            Math.min(deductionBeforeLimitAmount, ruleSnapshot.annualDeductionLimit());

        return new YouthLongTermCollectiveInvestmentCalculation(
            contributionAmount,
            deductionBeforeLimitAmount,
            youthLongTermCollectiveInvestmentDeductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required youth long-term collective investment deduction ruleCode: " + ruleCode
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

    public record YouthLongTermCollectiveInvestmentRuleSnapshot(
        long annualDeductionLimit,
        double deductionRate
    ) {
    }

    public record YouthLongTermCollectiveInvestmentCalculation(
        long youthLongTermCollectiveInvestmentContributionAmount,
        long deductionBeforeLimitAmount,
        long youthLongTermCollectiveInvestmentDeductionAmount
    ) {
    }
}
