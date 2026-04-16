package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class PensionInsurancePremiumCalculator {

    public static final String ELIGIBILITY_RULE_CODE = "PENSION_INSURANCE_PREMIUM_ELIGIBILITY_2025";
    public static final String PAID_AMOUNT_RULE_CODE = "PENSION_INSURANCE_PREMIUM_PAID_AMOUNT_DEDUCTION_2025";
    public static final String AGGREGATE_CAP_RULE_CODE = "PENSION_INSURANCE_PREMIUM_AGGREGATE_INCOME_CAP_2025";
    public static final String TRACE_RULE_CODE = "PENSION_INSURANCE_PREMIUM_TRACE_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public PensionInsurancePremiumCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PensionInsurancePremiumRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule eligibilityRule = findRule(ruleSetSnapshot, ELIGIBILITY_RULE_CODE);
        DeductionRule paidAmountRule = findRule(ruleSetSnapshot, PAID_AMOUNT_RULE_CODE);
        DeductionRule aggregateCapRule = findRule(ruleSetSnapshot, AGGREGATE_CAP_RULE_CODE);
        DeductionRule traceRule = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode eligibilityParameters = readParameters(eligibilityRule);
        JsonNode paidAmountParameters = readParameters(paidAmountRule);
        JsonNode aggregateCapParameters = readParameters(aggregateCapRule);
        JsonNode traceParameters = readParameters(traceRule);

        return new PensionInsurancePremiumRuleSnapshot(
            RuleReference.from(eligibilityRule),
            optionalBoolean(eligibilityParameters, "excludeEmployerContribution", true),
            RuleReference.from(paidAmountRule),
            optionalBoolean(paidAmountParameters, "floorAtZero", true),
            RuleReference.from(aggregateCapRule),
            readStringList(aggregateCapParameters, "includedDeductionFamilies"),
            RuleReference.from(traceRule),
            readStringList(traceParameters, "traceFields")
        );
    }

    public PensionInsurancePremiumCalculation calculate(
        long eligiblePaidAmount,
        long comprehensiveIncomeAmount,
        long priorIncludedDeductionsAmount,
        PensionInsurancePremiumRuleSnapshot ruleSnapshot
    ) {
        long effectivePaidAmount = ruleSnapshot.paidAmountFloorAtZero()
            ? Math.max(0L, eligiblePaidAmount)
            : eligiblePaidAmount;
        long aggregateCapRemainingAmount = Math.max(
            0L,
            comprehensiveIncomeAmount - Math.max(0L, priorIncludedDeductionsAmount)
        );
        long appliedAmount = Math.min(effectivePaidAmount, aggregateCapRemainingAmount);

        return new PensionInsurancePremiumCalculation(
            effectivePaidAmount,
            appliedAmount,
            aggregateCapRemainingAmount,
            ruleSnapshot
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.PENSION_INSURANCE_PREMIUM).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required pension insurance premium ruleCode: " + ruleCode
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

    private boolean optionalBoolean(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(value.asText());
    }

    private List<String> readStringList(JsonNode node, String fieldName) {
        List<String> values = new ArrayList<>();
        JsonNode array = node.get(fieldName);
        if (array == null || !array.isArray()) {
            return values;
        }
        for (JsonNode element : array) {
            if (element != null && !element.isNull()) {
                values.add(element.asText());
            }
        }
        return values;
    }

    public record PensionInsurancePremiumRuleSnapshot(
        RuleReference eligibilityRule,
        boolean excludeEmployerContribution,
        RuleReference paidAmountRule,
        boolean paidAmountFloorAtZero,
        RuleReference aggregateCapRule,
        List<String> includedDeductionFamilies,
        RuleReference traceRule,
        List<String> traceFields
    ) {
        public PensionInsurancePremiumRuleSnapshot {
            includedDeductionFamilies = List.copyOf(includedDeductionFamilies);
            traceFields = List.copyOf(traceFields);
        }
    }

    public record PensionInsurancePremiumCalculation(
        long eligiblePaidAmount,
        long appliedAmount,
        long aggregateCapRemainingAmount,
        PensionInsurancePremiumRuleSnapshot ruleSnapshot
    ) {
    }

    public record RuleReference(
        String ruleCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        static RuleReference from(DeductionRule rule) {
            return new RuleReference(rule.getRuleCode(), rule.getEffectiveFrom(), rule.getEffectiveTo());
        }
    }
}
