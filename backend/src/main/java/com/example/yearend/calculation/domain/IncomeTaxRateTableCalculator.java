package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class IncomeTaxRateTableCalculator {

    public static final String BRACKETS_RULE_CODE = "INCOME_TAX_BASIC_BRACKETS_2025";
    public static final String TAX_BASE_FORMULA_RULE_CODE = "COMPREHENSIVE_INCOME_TAX_BASE_FORMULA_2025";
    public static final String CALCULATED_TAX_FORMULA_RULE_CODE = "INCOME_TAX_CALCULATED_TAX_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public IncomeTaxRateTableCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IncomeTaxCalculation calculate(long taxableIncomeAmount, RuleSetSnapshot ruleSetSnapshot) {
        long taxBaseAmount = Math.max(0L, taxableIncomeAmount);
        IncomeTaxRateTableRuleSnapshot ruleSnapshot = resolveRuleSnapshot(ruleSetSnapshot);
        Bracket bracket = ruleSnapshot.match(taxBaseAmount);
        long calculatedTaxAmount = calculateTax(taxBaseAmount, bracket);

        return new IncomeTaxCalculation(
            taxBaseAmount,
            calculatedTaxAmount,
            bracket,
            ruleSnapshot.effectiveFrom(),
            ruleSnapshot.effectiveTo()
        );
    }

    IncomeTaxRateTableRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule bracketsRule = findRule(ruleSetSnapshot);
        return new IncomeTaxRateTableRuleSnapshot(
            parseBrackets(readParameters(bracketsRule)),
            bracketsRule.getEffectiveFrom(),
            bracketsRule.getEffectiveTo()
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot) {
        return ruleSetSnapshot.rulesFor(DeductionType.INCOME_TAX).stream()
            .filter(rule -> BRACKETS_RULE_CODE.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required income tax rate table ruleCode: " + BRACKETS_RULE_CODE
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

    private List<Bracket> parseBrackets(JsonNode parameters) {
        JsonNode bracketsNode = parameters.path("brackets");
        if (!bracketsNode.isArray() || bracketsNode.isEmpty()) {
            throw new IllegalStateException(BRACKETS_RULE_CODE + " must define a non-empty brackets array.");
        }

        List<JsonNode> sortedNodes = new ArrayList<>();
        bracketsNode.forEach(sortedNodes::add);
        sortedNodes.sort(Comparator
            .comparingLong(this::sequenceOrMax)
            .thenComparing(node -> optionalLong(node, "upToInclusive", "toInclusive", "upTo"), Comparator.nullsLast(Long::compareTo)));

        List<Bracket> brackets = new ArrayList<>();
        Long previousUpperBound = null;
        long previousTaxAtUpperBound = 0L;
        for (JsonNode node : sortedNodes) {
            Long upperBound = optionalLong(node, "upToInclusive", "toInclusive", "upTo");
            BigDecimal rate = requiredRate(node);
            Long quickDeductionAmount = optionalLong(node, "quickDeductionAmount");
            if (quickDeductionAmount == null) {
                quickDeductionAmount = deriveQuickDeduction(previousUpperBound, previousTaxAtUpperBound, rate);
            }
            Bracket bracket = new Bracket(
                optionalLong(node, "sequence"),
                optionalLong(node, "fromExclusive") == null ? previousUpperBound : optionalLong(node, "fromExclusive"),
                upperBound,
                rate,
                quickDeductionAmount
            );
            brackets.add(bracket);

            if (upperBound != null) {
                previousTaxAtUpperBound = calculateTax(upperBound, bracket);
                previousUpperBound = upperBound;
            }
        }

        return List.copyOf(brackets);
    }

    private long sequenceOrMax(JsonNode node) {
        Long sequence = optionalLong(node, "sequence");
        return sequence == null ? Long.MAX_VALUE : sequence;
    }

    private Long optionalLong(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.isNumber() ? value.longValue() : Long.parseLong(value.asText());
            }
        }
        return null;
    }

    private BigDecimal requiredRate(JsonNode node) {
        JsonNode rate = node.get("rate");
        if (rate != null && !rate.isNull()) {
            return rate.isNumber() ? rate.decimalValue() : new BigDecimal(rate.asText());
        }

        JsonNode ratePercent = node.get("ratePercent");
        if (ratePercent != null && !ratePercent.isNull()) {
            BigDecimal percent = ratePercent.isNumber() ? ratePercent.decimalValue() : new BigDecimal(ratePercent.asText());
            return percent.divide(BigDecimal.valueOf(100L), 10, RoundingMode.HALF_UP);
        }

        throw new IllegalStateException(BRACKETS_RULE_CODE + " is missing parameter: rate");
    }

    private long deriveQuickDeduction(Long previousUpperBound, long previousTaxAtUpperBound, BigDecimal rate) {
        if (previousUpperBound == null) {
            return 0L;
        }
        return BigDecimal.valueOf(previousUpperBound)
            .multiply(rate)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact() - previousTaxAtUpperBound;
    }

    private long calculateTax(long taxableIncomeAmount, Bracket bracket) {
        return BigDecimal.valueOf(Math.max(0L, taxableIncomeAmount))
            .multiply(bracket.rate())
            .setScale(0, RoundingMode.HALF_UP)
            .subtract(BigDecimal.valueOf(bracket.quickDeductionAmount()))
            .max(BigDecimal.ZERO)
            .longValueExact();
    }

    public record IncomeTaxCalculation(
        long taxableIncomeAmount,
        long calculatedTaxAmount,
        Bracket appliedBracket,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
    }

    record IncomeTaxRateTableRuleSnapshot(
        List<Bracket> brackets,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        IncomeTaxRateTableRuleSnapshot {
            brackets = List.copyOf(brackets);
            if (brackets.isEmpty()) {
                throw new IllegalArgumentException("Income tax rate table brackets must not be empty.");
            }
        }

        Bracket match(long taxableIncomeAmount) {
            return brackets.stream()
                .filter(bracket -> bracket.matches(taxableIncomeAmount))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No income tax rate table bracket matches taxable income amount " + taxableIncomeAmount + "."
                ));
        }
    }

    public record Bracket(
        Long sequence,
        Long fromExclusive,
        Long upToInclusive,
        BigDecimal rate,
        long quickDeductionAmount
    ) {

        boolean matches(long taxableIncomeAmount) {
            return (fromExclusive == null || taxableIncomeAmount > fromExclusive)
                && (upToInclusive == null || taxableIncomeAmount <= upToInclusive);
        }
    }
}
