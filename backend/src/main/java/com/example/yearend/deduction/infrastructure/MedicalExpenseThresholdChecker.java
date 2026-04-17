package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EligibilityCheckResult;
import com.example.yearend.deduction.domain.EligibilityChecker;
import com.example.yearend.deduction.domain.TaxContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 의료비 총급여 3% 임계율 자격 검사 (소득세법 제59조의4 제2항)
 * 임계율은 rule-pack의 MEDICAL_EXPENSE_THRESHOLD_RATE_2025에서 읽고,
 * 없으면 기본값 0.03을 사용한다.
 */
@Component
public class MedicalExpenseThresholdChecker implements EligibilityChecker {

    private static final double DEFAULT_THRESHOLD_RATE = 0.03;

    private final ObjectMapper objectMapper;

    public MedicalExpenseThresholdChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public EligibilityCheckResult check(TaxContext context, DeductionItem item) {
        if (item.getAmount() == null || item.getAmount() <= 0L) {
            return EligibilityCheckResult.fail("Medical expense amount must be greater than zero.");
        }

        double thresholdRate = resolveThresholdRate(context);
        long threshold = Math.round(context.totalSalary() * thresholdRate);
        if (item.getAmount() <= threshold) {
            return EligibilityCheckResult.fail(String.format(
                "Medical expense %,d원 does not exceed the %.0f%% salary threshold %,d원.",
                item.getAmount(), thresholdRate * 100, threshold
            ));
        }

        return EligibilityCheckResult.pass(String.format(
            "Medical expense %,d원 exceeds the %.0f%% salary threshold %,d원.",
            item.getAmount(), thresholdRate * 100, threshold
        ));
    }

    private double resolveThresholdRate(TaxContext context) {
        return context.ruleSetSnapshot()
            .rulesFor(DeductionType.MEDICAL_EXPENSE).stream()
            .filter(rule -> MedicalExpenseDeductionPolicy.THRESHOLD_RATE_RULE_CODE.equals(rule.getRuleCode()))
            .findFirst()
            .map(rule -> {
                try {
                    JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
                    return params.path("thresholdRate").asDouble(DEFAULT_THRESHOLD_RATE);
                } catch (Exception e) {
                    return DEFAULT_THRESHOLD_RATE;
                }
            })
            .orElse(DEFAULT_THRESHOLD_RATE);
    }
}
