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
public class CreditCardDeductionCalculator {

    public static final String MINIMUM_USAGE_THRESHOLD_RULE_CODE = "CREDIT_CARD_MINIMUM_USAGE_THRESHOLD_2025";
    public static final String RATE_CREDIT_RULE_CODE             = "CREDIT_CARD_RATE_CREDIT_2025";
    public static final String RATE_DEBIT_CASH_ZEROPAY_RULE_CODE = "CREDIT_CARD_RATE_DEBIT_CASH_ZEROPAY_2025";
    public static final String BASIC_LIMIT_RULE_CODE             = "CREDIT_CARD_BASIC_LIMIT_2025";
    public static final String TRACE_RULE_CODE                   = "CREDIT_CARD_TRACE_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public CreditCardDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreditCardRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule thresholdRule   = findRule(ruleSetSnapshot, MINIMUM_USAGE_THRESHOLD_RULE_CODE);
        DeductionRule rateCreditRule  = findRule(ruleSetSnapshot, RATE_CREDIT_RULE_CODE);
        DeductionRule rateDebitRule   = findRule(ruleSetSnapshot, RATE_DEBIT_CASH_ZEROPAY_RULE_CODE);
        DeductionRule basicLimitRule  = findRule(ruleSetSnapshot, BASIC_LIMIT_RULE_CODE);
        DeductionRule traceRule       = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode thresholdParameters  = readParameters(thresholdRule);
        JsonNode rateCreditParameters = readParameters(rateCreditRule);
        JsonNode rateDebitParameters  = readParameters(rateDebitRule);
        JsonNode basicLimitParameters = readParameters(basicLimitRule);
        JsonNode traceParameters      = readParameters(traceRule);

        double thresholdRate  = thresholdParameters.path("thresholdRate").asDouble(0.25);
        double creditRate     = rateCreditParameters.path("rate").asDouble(0.15);
        double debitCashRate  = rateDebitParameters.path("rate").asDouble(0.30);
        List<SalaryTier> tiers = readTiers(basicLimitParameters);

        return new CreditCardRuleSnapshot(
            RuleReference.from(thresholdRule),
            thresholdRate,
            RuleReference.from(rateCreditRule),
            creditRate,
            RuleReference.from(rateDebitRule),
            debitCashRate,
            RuleReference.from(basicLimitRule),
            tiers,
            RuleReference.from(traceRule),
            readStringList(traceParameters, "traceFields")
        );
    }

    public CreditCardCalculation calculate(
        long creditCardAmount,
        long debitCardAmount,
        long cashReceiptAmount,
        long zeroPayAmount,
        long totalSalary,
        CreditCardRuleSnapshot ruleSnapshot
    ) {
        long effectiveCreditCard  = Math.max(0L, creditCardAmount);
        long effectiveDebitCard   = Math.max(0L, debitCardAmount);
        long effectiveCashReceipt = Math.max(0L, cashReceiptAmount);
        long effectiveZeroPay     = Math.max(0L, zeroPayAmount);
        long totalUsageAmount     = effectiveCreditCard + effectiveDebitCard + effectiveCashReceipt + effectiveZeroPay;

        long minimumUsageThresholdAmount = Math.round(totalSalary * ruleSnapshot.thresholdRate());

        if (totalUsageAmount <= minimumUsageThresholdAmount) {
            return zeroCalculation(effectiveCreditCard, effectiveDebitCard, effectiveCashReceipt,
                effectiveZeroPay, totalUsageAmount, minimumUsageThresholdAmount, ruleSnapshot);
        }

        // Apply threshold sequentially: credit card (lowest rate) first, then debit/cash/zeropay
        long remainingThreshold = minimumUsageThresholdAmount;

        long creditAboveThreshold = Math.max(0L, effectiveCreditCard - remainingThreshold);
        remainingThreshold = Math.max(0L, remainingThreshold - effectiveCreditCard);

        long debitAboveThreshold   = Math.max(0L, effectiveDebitCard   - remainingThreshold);
        remainingThreshold = Math.max(0L, remainingThreshold - effectiveDebitCard);

        long cashAboveThreshold    = Math.max(0L, effectiveCashReceipt - remainingThreshold);
        remainingThreshold = Math.max(0L, remainingThreshold - effectiveCashReceipt);

        long zeroPayAboveThreshold = Math.max(0L, effectiveZeroPay - remainingThreshold);

        long creditCardAboveThresholdAmount       = creditAboveThreshold;
        long debitCashZeroPayAboveThresholdAmount = debitAboveThreshold + cashAboveThreshold + zeroPayAboveThreshold;

        long deductionBeforeLimitAmount = Math.round(creditCardAboveThresholdAmount * ruleSnapshot.creditRate())
            + Math.round(debitCashZeroPayAboveThresholdAmount * ruleSnapshot.debitCashRate());

        long basicLimitAmount = resolveBasicLimit(totalSalary, ruleSnapshot.tiers());
        long appliedAmount    = Math.min(deductionBeforeLimitAmount, basicLimitAmount);

        return new CreditCardCalculation(
            effectiveCreditCard,
            effectiveDebitCard,
            effectiveCashReceipt,
            effectiveZeroPay,
            totalUsageAmount,
            minimumUsageThresholdAmount,
            creditCardAboveThresholdAmount,
            debitCashZeroPayAboveThresholdAmount,
            deductionBeforeLimitAmount,
            basicLimitAmount,
            appliedAmount,
            ruleSnapshot
        );
    }

    private CreditCardCalculation zeroCalculation(
        long effectiveCreditCard,
        long effectiveDebitCard,
        long effectiveCashReceipt,
        long effectiveZeroPay,
        long totalUsageAmount,
        long minimumUsageThresholdAmount,
        CreditCardRuleSnapshot ruleSnapshot
    ) {
        long basicLimitAmount = resolveBasicLimit(0L, ruleSnapshot.tiers());
        return new CreditCardCalculation(
            effectiveCreditCard,
            effectiveDebitCard,
            effectiveCashReceipt,
            effectiveZeroPay,
            totalUsageAmount,
            minimumUsageThresholdAmount,
            0L,
            0L,
            0L,
            basicLimitAmount,
            0L,
            ruleSnapshot
        );
    }

    private long resolveBasicLimit(long totalSalary, List<SalaryTier> tiers) {
        for (SalaryTier tier : tiers) {
            if (tier.maxSalary() == null || totalSalary <= tier.maxSalary()) {
                return tier.limit();
            }
        }
        return tiers.isEmpty() ? 3_000_000L : tiers.getLast().limit();
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.CREDIT_CARD).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required credit card deduction ruleCode: " + ruleCode
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

    private List<SalaryTier> readTiers(JsonNode parameters) {
        List<SalaryTier> tiers = new ArrayList<>();
        JsonNode tiersNode = parameters.get("tiers");
        if (tiersNode == null || !tiersNode.isArray()) {
            return tiers;
        }
        for (JsonNode tierNode : tiersNode) {
            JsonNode maxSalaryNode = tierNode.get("maxSalary");
            Long maxSalary = (maxSalaryNode == null || maxSalaryNode.isNull()) ? null : maxSalaryNode.asLong();
            long limit = tierNode.path("limit").asLong(3_000_000L);
            tiers.add(new SalaryTier(maxSalary, limit));
        }
        return tiers;
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

    public record CreditCardRuleSnapshot(
        RuleReference minimumUsageThresholdRule,
        double thresholdRate,
        RuleReference rateCreditRule,
        double creditRate,
        RuleReference rateDebitCashZeroPayRule,
        double debitCashRate,
        RuleReference basicLimitRule,
        List<SalaryTier> tiers,
        RuleReference traceRule,
        List<String> traceFields
    ) {
        public CreditCardRuleSnapshot {
            tiers = List.copyOf(tiers);
            traceFields = List.copyOf(traceFields);
        }
    }

    public record CreditCardCalculation(
        long creditCardAmount,
        long debitCardAmount,
        long cashReceiptAmount,
        long zeroPayAmount,
        long totalUsageAmount,
        long minimumUsageThresholdAmount,
        long creditCardAboveThresholdAmount,
        long debitCashZeroPayAboveThresholdAmount,
        long deductionBeforeLimitAmount,
        long basicLimitAmount,
        long appliedAmount,
        CreditCardRuleSnapshot ruleSnapshot
    ) {
    }

    public record SalaryTier(
        Long maxSalary,
        long limit
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
