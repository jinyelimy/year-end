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
public class EmployeeStockOwnershipDeductionCalculator {

    public static final String RATE_RULE_CODE  = "EMPLOYEE_STOCK_OWNERSHIP_RATE_2025";
    public static final String LIMIT_RULE_CODE = "EMPLOYEE_STOCK_OWNERSHIP_LIMIT_2025";
    public static final String TRACE_RULE_CODE = "EMPLOYEE_STOCK_OWNERSHIP_TRACE_2025";

    private final ObjectMapper objectMapper;

    public EmployeeStockOwnershipDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EmployeeStockOwnershipRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule traceRule = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode rateParameters  = readParameters(rateRule);
        JsonNode limitParameters = readParameters(limitRule);
        JsonNode traceParameters = readParameters(traceRule);

        double deductionRate              = rateParameters.path("deductionRate").asDouble(1.00);
        long regularAnnualDeductionLimit  = limitParameters.path("regularAnnualDeductionLimit").asLong(4_000_000L);
        long ventureAnnualDeductionLimit  = limitParameters.path("ventureAnnualDeductionLimit").asLong(15_000_000L);
        List<String> traceFields          = readTraceFields(traceParameters);

        return new EmployeeStockOwnershipRuleSnapshot(
            regularAnnualDeductionLimit,
            ventureAnnualDeductionLimit,
            deductionRate,
            traceFields
        );
    }

    public EmployeeStockOwnershipCalculation calculate(
        long employeeStockOwnershipContributionAmount,
        boolean isVentureEmployer,
        EmployeeStockOwnershipRuleSnapshot ruleSnapshot
    ) {
        long contributionAmount = Math.max(0L, employeeStockOwnershipContributionAmount);
        long appliedLimit = isVentureEmployer
            ? ruleSnapshot.ventureAnnualDeductionLimit()
            : ruleSnapshot.regularAnnualDeductionLimit();
        long deductionBeforeLimitAmount = Math.round(contributionAmount * ruleSnapshot.deductionRate());
        long employeeStockOwnershipDeductionAmount = Math.max(
            0L,
            Math.min(deductionBeforeLimitAmount, appliedLimit)
        );

        return new EmployeeStockOwnershipCalculation(
            contributionAmount,
            appliedLimit,
            employeeStockOwnershipDeductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.EMPLOYEE_STOCK_OWNERSHIP).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required employee stock ownership deduction ruleCode: " + ruleCode
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

    public record EmployeeStockOwnershipRuleSnapshot(
        long regularAnnualDeductionLimit,
        long ventureAnnualDeductionLimit,
        double deductionRate,
        List<String> traceFields
    ) {
        public EmployeeStockOwnershipRuleSnapshot {
            traceFields = List.copyOf(traceFields);
        }
    }

    public record EmployeeStockOwnershipCalculation(
        long employeeStockOwnershipContributionAmount,
        long employeeStockOwnershipAppliedLimitAmount,
        long employeeStockOwnershipDeductionAmount
    ) {
    }
}
