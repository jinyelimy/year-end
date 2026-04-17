package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class HousingLoanDeductionCalculator {

    public static final String LIMIT_RULE_CODE = "HOUSING_LOAN_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "HOUSING_LOAN_RATE_2025";
    public static final String TRACE_RULE_CODE = "HOUSING_LOAN_TRACE_2025";

    private final ObjectMapper objectMapper;

    public HousingLoanDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HousingLoanRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);

        JsonNode limitParameters = readParameters(limitRule);
        JsonNode rateParameters  = readParameters(rateRule);

        long annualDeductionLimit = limitParameters.path("annualDeductionLimit").asLong(4_000_000L);
        double deductionRate      = rateParameters.path("deductionRate").asDouble(0.40);

        return new HousingLoanRuleSnapshot(annualDeductionLimit, deductionRate);
    }

    public HousingLoanCalculation calculate(
        long housingLoanBankRepaymentAmount,
        long housingLoanIndividualRepaymentAmount,
        HousingLoanRuleSnapshot ruleSnapshot
    ) {
        long bankAmount       = Math.max(0L, housingLoanBankRepaymentAmount);
        long individualAmount = Math.max(0L, housingLoanIndividualRepaymentAmount);
        long totalRepaymentAmount       = bankAmount + individualAmount;
        long deductionBeforeLimitAmount = (long)(totalRepaymentAmount * ruleSnapshot.deductionRate());
        long housingLoanDeductionAmount = Math.min(deductionBeforeLimitAmount, ruleSnapshot.annualDeductionLimit());

        return new HousingLoanCalculation(
            bankAmount,
            individualAmount,
            totalRepaymentAmount,
            deductionBeforeLimitAmount,
            housingLoanDeductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.HOUSING_LOAN).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required housing loan deduction ruleCode: " + ruleCode
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

    public record HousingLoanRuleSnapshot(
        long annualDeductionLimit,
        double deductionRate
    ) {
    }

    public record HousingLoanCalculation(
        long housingLoanBankRepaymentAmount,
        long housingLoanIndividualRepaymentAmount,
        long totalRepaymentAmount,
        long deductionBeforeLimitAmount,
        long housingLoanDeductionAmount
    ) {
    }
}
