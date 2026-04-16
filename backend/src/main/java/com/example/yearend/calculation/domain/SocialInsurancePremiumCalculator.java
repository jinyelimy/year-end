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
public class SocialInsurancePremiumCalculator {

    public static final String HEALTH_ELIGIBILITY_RULE_CODE = "SOCIAL_INSURANCE_PREMIUM_HEALTH_ELIGIBILITY_2025";
    public static final String EMPLOYMENT_ELIGIBILITY_RULE_CODE = "SOCIAL_INSURANCE_PREMIUM_EMPLOYMENT_ELIGIBILITY_2025";
    public static final String AGGREGATE_CAP_RULE_CODE = "SOCIAL_INSURANCE_PREMIUM_AGGREGATE_CAP_2025";
    public static final String TRACE_RULE_CODE = "SOCIAL_INSURANCE_PREMIUM_TRACE_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public SocialInsurancePremiumCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SocialInsurancePremiumRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule healthEligibilityRule = findRule(ruleSetSnapshot, HEALTH_ELIGIBILITY_RULE_CODE);
        DeductionRule employmentEligibilityRule = findRule(ruleSetSnapshot, EMPLOYMENT_ELIGIBILITY_RULE_CODE);
        DeductionRule aggregateCapRule = findRule(ruleSetSnapshot, AGGREGATE_CAP_RULE_CODE);
        DeductionRule traceRule = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode aggregateCapParameters = readParameters(aggregateCapRule);
        JsonNode traceParameters = readParameters(traceRule);

        return new SocialInsurancePremiumRuleSnapshot(
            RuleReference.from(healthEligibilityRule),
            RuleReference.from(employmentEligibilityRule),
            RuleReference.from(aggregateCapRule),
            readStringList(aggregateCapParameters, "includedDeductionFamilies"),
            RuleReference.from(traceRule),
            readStringList(traceParameters, "traceFields")
        );
    }

    public SocialInsurancePremiumCalculation calculate(
        long healthInsurancePremiumAmount,
        long employmentInsurancePremiumAmount,
        long comprehensiveIncomeAmount,
        long priorIncludedDeductionsAmount,
        SocialInsurancePremiumRuleSnapshot ruleSnapshot
    ) {
        long effectiveHealthAmount = Math.max(0L, healthInsurancePremiumAmount);
        long effectiveEmploymentAmount = Math.max(0L, employmentInsurancePremiumAmount);
        long totalEligibleAmount = effectiveHealthAmount + effectiveEmploymentAmount;
        long aggregateCapRemainingAmount = Math.max(
            0L,
            comprehensiveIncomeAmount - Math.max(0L, priorIncludedDeductionsAmount)
        );
        long appliedAmount = Math.min(totalEligibleAmount, aggregateCapRemainingAmount);

        return new SocialInsurancePremiumCalculation(
            effectiveHealthAmount,
            effectiveEmploymentAmount,
            totalEligibleAmount,
            appliedAmount,
            aggregateCapRemainingAmount,
            ruleSnapshot
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.SOCIAL_INSURANCE_PREMIUM).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required social insurance premium ruleCode: " + ruleCode
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

    public record SocialInsurancePremiumRuleSnapshot(
        RuleReference healthEligibilityRule,
        RuleReference employmentEligibilityRule,
        RuleReference aggregateCapRule,
        List<String> includedDeductionFamilies,
        RuleReference traceRule,
        List<String> traceFields
    ) {
        public SocialInsurancePremiumRuleSnapshot {
            includedDeductionFamilies = List.copyOf(includedDeductionFamilies);
            traceFields = List.copyOf(traceFields);
        }
    }

    public record SocialInsurancePremiumCalculation(
        long effectiveHealthAmount,
        long effectiveEmploymentAmount,
        long totalEligibleAmount,
        long appliedAmount,
        long aggregateCapRemainingAmount,
        SocialInsurancePremiumRuleSnapshot ruleSnapshot
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
