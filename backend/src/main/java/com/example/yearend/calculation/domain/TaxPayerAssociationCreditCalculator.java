package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 납세조합공제 (소득세법 제150조)
 *
 * 납세조합이 징수한 근로소득에 대하여 산출세액의 일정 비율(5%)을
 * 세액공제한다.
 *
 * 공제액 = 산출세액 × (납세조합징수근로소득금액 / 근로소득금액) × 5%
 */
@Component
public class TaxPayerAssociationCreditCalculator {

    public static final String RATE_RULE_CODE    = "TAX_PAYER_ASSOCIATION_CREDIT_RATE_2025";
    public static final String FORMULA_RULE_CODE = "TAX_PAYER_ASSOCIATION_CREDIT_FORMULA_2025";
    public static final String TRACE_RULE_CODE   = "TAX_PAYER_ASSOCIATION_CREDIT_TRACE_2025";

    private final ObjectMapper objectMapper;

    public TaxPayerAssociationCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TaxPayerAssociationCreditRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule rateRule    = findRule(ruleSetSnapshot, RATE_RULE_CODE);
        DeductionRule formulaRule = findRule(ruleSetSnapshot, FORMULA_RULE_CODE);
        DeductionRule traceRule   = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode rateParameters  = readParameters(rateRule);
        // formulaRule 파라미터는 문서화 목적으로만 조회 — 계산 로직은 Calculator 내부 고정.
        readParameters(formulaRule);
        JsonNode traceParameters = readParameters(traceRule);

        double creditRate        = rateParameters.path("creditRate").asDouble(0.05);
        List<String> traceFields = readTraceFields(traceParameters);

        return new TaxPayerAssociationCreditRuleSnapshot(creditRate, traceFields);
    }

    public TaxPayerAssociationCreditCalculation calculate(
        long taxPayerAssociationIncomeAmount,
        long earnedIncomeAmount,
        long domesticCalculatedTaxAmount,
        TaxPayerAssociationCreditRuleSnapshot ruleSnapshot
    ) {
        long associationIncome = Math.max(0L, taxPayerAssociationIncomeAmount);
        long earnedIncome      = Math.max(0L, earnedIncomeAmount);
        long calculatedTax     = Math.max(0L, domesticCalculatedTaxAmount);

        if (earnedIncome <= 0L) {
            return new TaxPayerAssociationCreditCalculation(
                associationIncome,
                earnedIncome,
                calculatedTax,
                0L,
                0L
            );
        }

        long allocatedTax = Math.round(calculatedTax * ((double) associationIncome / earnedIncome));
        allocatedTax = Math.max(0L, allocatedTax);

        long creditAmount = Math.round(allocatedTax * ruleSnapshot.creditRate());
        creditAmount = Math.max(0L, creditAmount);

        return new TaxPayerAssociationCreditCalculation(
            associationIncome,
            earnedIncome,
            calculatedTax,
            allocatedTax,
            creditAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.TAX_PAYER_ASSOCIATION_CREDIT).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required tax payer association credit ruleCode: " + ruleCode
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

    public record TaxPayerAssociationCreditRuleSnapshot(
        double creditRate,
        List<String> traceFields
    ) {
        public TaxPayerAssociationCreditRuleSnapshot {
            traceFields = List.copyOf(traceFields);
        }
    }

    public record TaxPayerAssociationCreditCalculation(
        long taxPayerAssociationIncomeAmount,
        long earnedIncomeAmount,
        long domesticCalculatedTaxAmount,
        long taxPayerAssociationAllocatedTaxAmount,
        long taxPayerAssociationCreditAmount
    ) {
    }
}
