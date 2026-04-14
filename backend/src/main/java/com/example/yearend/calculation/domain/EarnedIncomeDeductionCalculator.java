package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class EarnedIncomeDeductionCalculator {

    static final String BRACKETS_RULE_CODE = "EARNED_INCOME_DEDUCTION_BRACKETS";
    static final String MAX_LIMIT_RULE_CODE = "EARNED_INCOME_DEDUCTION_MAX_LIMIT";

    private final ObjectMapper objectMapper;

    public EarnedIncomeDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EarnedIncomeDeductionRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule bracketsRule = findRule(ruleSetSnapshot, BRACKETS_RULE_CODE);
        DeductionRule maxLimitRule = findRule(ruleSetSnapshot, MAX_LIMIT_RULE_CODE);

        JsonNode bracketsParameters = readParameters(bracketsRule);
        JsonNode maxLimitParameters = readParameters(maxLimitRule);

        List<EarnedIncomeDeductionRuleSnapshot.Bracket> brackets = parseBrackets(bracketsParameters);
        long maximumDeductionAmount = requiredLong(maxLimitParameters, "limitAmount", MAX_LIMIT_RULE_CODE);

        return new EarnedIncomeDeductionRuleSnapshot(
            brackets,
            maximumDeductionAmount,
            bracketsRule.getEffectiveFrom(),
            bracketsRule.getEffectiveTo(),
            maxLimitRule.getEffectiveFrom(),
            maxLimitRule.getEffectiveTo()
        );
    }

    public long calculate(long taxableSalaryAmount, EarnedIncomeDeductionRuleSnapshot ruleSnapshot) {
        long baseAmount = Math.max(0L, taxableSalaryAmount);
        EarnedIncomeDeductionRuleSnapshot.Bracket bracket = ruleSnapshot.match(baseAmount);
        long deductionAmount = bracket.baseDeductionAmount()
            + percentage(baseAmount - bracket.excessBaseAmount(), bracket.excessRatePercent());

        return Math.min(deductionAmount, ruleSnapshot.maximumDeductionAmount());
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rules().stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required earned income deduction ruleCode: " + ruleCode
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

    private List<EarnedIncomeDeductionRuleSnapshot.Bracket> parseBrackets(JsonNode parameters) {
        JsonNode bracketsNode = parameters.path("brackets");
        if (!bracketsNode.isArray() || bracketsNode.isEmpty()) {
            throw new IllegalStateException(BRACKETS_RULE_CODE + " must define a non-empty brackets array.");
        }

        return parseBracketNodes(bracketsNode);
    }

    private List<EarnedIncomeDeductionRuleSnapshot.Bracket> parseBracketNodes(JsonNode bracketsNode) {
        return java.util.stream.StreamSupport.stream(bracketsNode.spliterator(), false)
            .map(node -> new EarnedIncomeDeductionRuleSnapshot.Bracket(
                requiredLong(node, "fromInclusive", BRACKETS_RULE_CODE),
                optionalLong(node, "toInclusive"),
                requiredLong(node, "baseDeductionAmount", BRACKETS_RULE_CODE),
                requiredLong(node, "excessBaseAmount", BRACKETS_RULE_CODE),
                requiredDecimal(node, "excessRatePercent", BRACKETS_RULE_CODE)
            ))
            .sorted(Comparator.comparingLong(EarnedIncomeDeductionRuleSnapshot.Bracket::fromInclusive))
            .toList();
    }

    private long requiredLong(JsonNode node, String fieldName, String ruleCode) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(ruleCode + " is missing parameter: " + fieldName);
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        return Long.parseLong(value.asText());
    }

    private Long optionalLong(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        return Long.parseLong(value.asText());
    }

    private BigDecimal requiredDecimal(JsonNode node, String fieldName, String ruleCode) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(ruleCode + " is missing parameter: " + fieldName);
        }
        return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
    }

    private long percentage(long amount, BigDecimal ratePercent) {
        return BigDecimal.valueOf(Math.max(0L, amount))
            .multiply(ratePercent)
            .divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP)
            .longValueExact();
    }

    public record EarnedIncomeDeductionRuleSnapshot(
        List<Bracket> brackets,
        long maximumDeductionAmount,
        LocalDate bracketsEffectiveFrom,
        LocalDate bracketsEffectiveTo,
        LocalDate maxLimitEffectiveFrom,
        LocalDate maxLimitEffectiveTo
    ) {

        public EarnedIncomeDeductionRuleSnapshot {
            brackets = List.copyOf(brackets);
            if (brackets.isEmpty()) {
                throw new IllegalArgumentException("Earned income deduction brackets must not be empty.");
            }
        }

        Bracket match(long taxableSalaryAmount) {
            return brackets.stream()
                .filter(bracket -> bracket.matches(taxableSalaryAmount))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No earned income deduction bracket matches taxable salary amount " + taxableSalaryAmount + "."
                ));
        }

        public record Bracket(
            long fromInclusive,
            Long toInclusive,
            long baseDeductionAmount,
            long excessBaseAmount,
            BigDecimal excessRatePercent
        ) {

            boolean matches(long taxableSalaryAmount) {
                return taxableSalaryAmount >= fromInclusive
                    && (toInclusive == null || taxableSalaryAmount <= toInclusive);
            }
        }
    }
}
