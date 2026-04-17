package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LongTermMortgageDeductionCalculator {

    public static final String LIMIT_RULE_CODE = "LONG_TERM_MORTGAGE_LIMIT_2025";
    public static final String TRACE_RULE_CODE = "LONG_TERM_MORTGAGE_TRACE_2025";

    public static final String TYPE_NON_INSTALLMENT_FIXED    = "NON_INSTALLMENT_FIXED";
    public static final String TYPE_NON_INSTALLMENT_OR_FIXED = "NON_INSTALLMENT_OR_FIXED";
    public static final String TYPE_OTHER_15Y                = "OTHER_15Y";
    public static final String TYPE_UNDER_15Y                = "UNDER_15Y";

    private final ObjectMapper objectMapper;

    public LongTermMortgageDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LongTermMortgageRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        JsonNode limitParameters = readParameters(limitRule);

        Map<String, Long> tierLimits = new LinkedHashMap<>();
        JsonNode tiersNode = limitParameters.path("tiers");
        if (tiersNode.isArray()) {
            for (JsonNode tier : tiersNode) {
                String repaymentType = tier.path("repaymentType").asText();
                long limitAmount = tier.path("limitAmount").asLong(0L);
                tierLimits.put(repaymentType, limitAmount);
            }
        }

        return new LongTermMortgageRuleSnapshot(Map.copyOf(tierLimits));
    }

    public LongTermMortgageCalculation calculate(
        long longTermMortgageInterestAmount,
        String longTermMortgageRepaymentType,
        LongTermMortgageRuleSnapshot ruleSnapshot
    ) {
        long interestAmount = Math.max(0L, longTermMortgageInterestAmount);
        long limitAmount = ruleSnapshot.limitForType(longTermMortgageRepaymentType);
        long deductionAmount = Math.min(interestAmount, limitAmount);

        return new LongTermMortgageCalculation(
            interestAmount,
            longTermMortgageRepaymentType,
            limitAmount,
            deductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.LONG_TERM_MORTGAGE).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required long-term mortgage deduction ruleCode: " + ruleCode
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

    public record LongTermMortgageRuleSnapshot(
        Map<String, Long> tierLimits
    ) {
        public long limitForType(String repaymentType) {
            if (repaymentType == null) {
                return 0L;
            }
            return tierLimits.getOrDefault(repaymentType, 0L);
        }
    }

    public record LongTermMortgageCalculation(
        long longTermMortgageInterestAmount,
        String longTermMortgageRepaymentType,
        long longTermMortgageLimitAmount,
        long longTermMortgageDeductionAmount
    ) {
    }
}
