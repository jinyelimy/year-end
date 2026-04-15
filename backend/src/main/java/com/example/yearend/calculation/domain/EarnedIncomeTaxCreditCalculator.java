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
public class EarnedIncomeTaxCreditCalculator {

    public static final String BASE_FORMULA_RULE_CODE = "EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025";
    public static final String LIMIT_BY_GROSS_SALARY_RULE_CODE = "EARNED_INCOME_TAX_CREDIT_LIMIT_BY_GROSS_SALARY_2025";
    public static final String FINAL_FORMULA_RULE_CODE = "EARNED_INCOME_TAX_CREDIT_FINAL_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public EarnedIncomeTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EarnedIncomeTaxCreditRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule baseFormulaRule = findRule(ruleSetSnapshot, BASE_FORMULA_RULE_CODE);
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_BY_GROSS_SALARY_RULE_CODE);
        DeductionRule finalFormulaRule = findRule(ruleSetSnapshot, FINAL_FORMULA_RULE_CODE);
        JsonNode finalFormulaParameters = readParameters(finalFormulaRule);

        return new EarnedIncomeTaxCreditRuleSnapshot(
            RuleReference.from(baseFormulaRule),
            parseCreditBrackets(readParameters(baseFormulaRule)),
            RuleReference.from(limitRule),
            parseLimitBrackets(readParameters(limitRule)),
            RuleReference.from(finalFormulaRule),
            parseFinalFormula(finalFormulaParameters, baseFormulaRule.getRuleCode(), limitRule.getRuleCode())
        );
    }

    public EarnedIncomeTaxCreditCalculation calculate(
        long totalGrossSalaryAmount,
        long calculatedTaxAmount,
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot
    ) {
        if (totalGrossSalaryAmount <= 0L || calculatedTaxAmount <= 0L) {
            return new EarnedIncomeTaxCreditCalculation(0L, 0L, 0L, ruleSnapshot);
        }

        long baseCreditAmount = calculateBaseCredit(calculatedTaxAmount, ruleSnapshot.matchCreditBracket(calculatedTaxAmount));
        long salaryBasedLimitAmount = calculateLimit(
            totalGrossSalaryAmount,
            ruleSnapshot.matchLimitBracket(totalGrossSalaryAmount)
        );
        long earnedIncomeTaxCreditAmount = ruleSnapshot.finalFormula().apply(baseCreditAmount, salaryBasedLimitAmount);

        return new EarnedIncomeTaxCreditCalculation(
            baseCreditAmount,
            salaryBasedLimitAmount,
            earnedIncomeTaxCreditAmount,
            ruleSnapshot
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.INCOME_TAX).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required earned income tax credit ruleCode: " + ruleCode
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

    private List<CreditBracket> parseCreditBrackets(JsonNode parameters) {
        JsonNode bracketsNode = parameters.path("brackets");
        if (!bracketsNode.isArray() || bracketsNode.isEmpty()) {
            throw new IllegalStateException(BASE_FORMULA_RULE_CODE + " must define a non-empty brackets array.");
        }

        List<CreditBracket> brackets = new ArrayList<>();
        bracketsNode.forEach(node -> brackets.add(new CreditBracket(
            optionalLong(node, "sequence"),
            optionalLong(node, "fromExclusive"),
            optionalLong(node, "upToInclusive", "toInclusive", "upTo"),
            requiredLong(node, "baseCreditAmount", BASE_FORMULA_RULE_CODE),
            requiredLong(node, "excessBaseAmount", BASE_FORMULA_RULE_CODE),
            requiredRate(node, BASE_FORMULA_RULE_CODE)
        )));

        return brackets.stream()
            .sorted(Comparator
                .comparingLong((CreditBracket bracket) -> sequenceOrMax(bracket))
                .thenComparing(CreditBracket::upToInclusive, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private FinalFormula parseFinalFormula(JsonNode parameters, String baseRuleCode, String limitRuleCode) {
        String formula = requiredText(parameters, "formula", FINAL_FORMULA_RULE_CODE);
        String referencedBaseRuleCode = requiredText(parameters, "baseRuleCode", FINAL_FORMULA_RULE_CODE);
        String referencedLimitRuleCode = requiredText(parameters, "limitRuleCode", FINAL_FORMULA_RULE_CODE);
        if (!baseRuleCode.equals(referencedBaseRuleCode) || !limitRuleCode.equals(referencedLimitRuleCode)) {
            throw new IllegalStateException(
                FINAL_FORMULA_RULE_CODE + " references ruleCodes that are not present in the resolved snapshot."
            );
        }
        return FinalFormula.from(formula);
    }

    private List<LimitBracket> parseLimitBrackets(JsonNode parameters) {
        JsonNode bracketsNode = parameters.path("limitBrackets");
        if (!bracketsNode.isArray() || bracketsNode.isEmpty()) {
            throw new IllegalStateException(LIMIT_BY_GROSS_SALARY_RULE_CODE + " must define a non-empty limitBrackets array.");
        }

        List<LimitBracket> brackets = new ArrayList<>();
        bracketsNode.forEach(node -> brackets.add(new LimitBracket(
            optionalLong(node, "sequence"),
            optionalLong(node, "fromExclusive"),
            optionalLong(node, "upToInclusive", "toInclusive", "upTo"),
            optionalLong(node, "limitAmount"),
            optionalLong(node, "baseLimitAmount"),
            optionalLong(node, "reductionBaseAmount"),
            optionalRate(node, "reductionRate", "rate"),
            optionalPercentRate(node, "reductionRatePercent", "ratePercent"),
            optionalLong(node, "minimumLimitAmount")
        )));

        return brackets.stream()
            .sorted(Comparator
                .comparingLong((LimitBracket bracket) -> sequenceOrMax(bracket))
                .thenComparing(LimitBracket::upToInclusive, Comparator.nullsLast(Long::compareTo)))
            .toList();
    }

    private long calculateBaseCredit(long calculatedTaxAmount, CreditBracket bracket) {
        return bracket.baseCreditAmount()
            + multiplyRate(calculatedTaxAmount - bracket.excessBaseAmount(), bracket.effectiveRate());
    }

    private long calculateLimit(long totalGrossSalaryAmount, LimitBracket bracket) {
        if (bracket.limitAmount() != null) {
            return Math.max(0L, bracket.limitAmount());
        }
        if (bracket.baseLimitAmount() == null
            || bracket.reductionBaseAmount() == null
            || bracket.effectiveReductionRate() == null
            || bracket.minimumLimitAmount() == null) {
            throw new IllegalStateException(
                LIMIT_BY_GROSS_SALARY_RULE_CODE + " is missing limit reduction parameters."
            );
        }

        long reductionAmount = multiplyRate(
            totalGrossSalaryAmount - bracket.reductionBaseAmount(),
            bracket.effectiveReductionRate()
        );
        long reducedLimit = bracket.baseLimitAmount() - reductionAmount;
        return Math.max(bracket.minimumLimitAmount(), reducedLimit);
    }

    private long sequenceOrMax(CreditBracket bracket) {
        return bracket.sequence() == null ? Long.MAX_VALUE : bracket.sequence();
    }

    private long sequenceOrMax(LimitBracket bracket) {
        return bracket.sequence() == null ? Long.MAX_VALUE : bracket.sequence();
    }

    private long requiredLong(JsonNode node, String fieldName, String ruleCode) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(ruleCode + " is missing parameter: " + fieldName);
        }
        return value.isNumber() ? value.longValue() : Long.parseLong(value.asText());
    }

    private String requiredText(JsonNode node, String fieldName, String ruleCode) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalStateException(ruleCode + " is missing parameter: " + fieldName);
        }
        return value.asText();
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

    private BigDecimal requiredRate(JsonNode node, String ruleCode) {
        BigDecimal decimalRate = optionalRate(node, "excessCreditRate", "creditRate");
        if (decimalRate != null) {
            return decimalRate;
        }

        BigDecimal percentRate = optionalPercentRate(node, "excessCreditRatePercent", "creditRatePercent");
        if (percentRate != null) {
            return percentRate;
        }

        throw new IllegalStateException(
            ruleCode + " is missing parameter: excessCreditRate or excessCreditRatePercent"
        );
    }

    private BigDecimal optionalRate(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
            }
        }
        return null;
    }

    private BigDecimal optionalPercentRate(JsonNode node, String... fieldNames) {
        BigDecimal percent = optionalRate(node, fieldNames);
        if (percent == null) {
            return null;
        }
        return percent.divide(BigDecimal.valueOf(100L), 10, RoundingMode.HALF_UP);
    }

    private long multiplyRate(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(Math.max(0L, amount))
            .multiply(rate)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
    }

    public record EarnedIncomeTaxCreditCalculation(
        long baseCreditAmount,
        long salaryBasedLimitAmount,
        long earnedIncomeTaxCreditAmount,
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot
    ) {
    }

    public record EarnedIncomeTaxCreditRuleSnapshot(
        RuleReference baseFormulaRule,
        List<CreditBracket> creditBrackets,
        RuleReference limitRule,
        List<LimitBracket> limitBrackets,
        RuleReference finalFormulaRule,
        FinalFormula finalFormula
    ) {

        public EarnedIncomeTaxCreditRuleSnapshot {
            creditBrackets = List.copyOf(creditBrackets);
            limitBrackets = List.copyOf(limitBrackets);
            if (creditBrackets.isEmpty()) {
                throw new IllegalArgumentException("Earned income tax credit brackets must not be empty.");
            }
            if (limitBrackets.isEmpty()) {
                throw new IllegalArgumentException("Earned income tax credit limit brackets must not be empty.");
            }
        }

        public LocalDate baseFormulaEffectiveFrom() {
            return baseFormulaRule.effectiveFrom();
        }

        public LocalDate baseFormulaEffectiveTo() {
            return baseFormulaRule.effectiveTo();
        }

        public LocalDate limitEffectiveFrom() {
            return limitRule.effectiveFrom();
        }

        public LocalDate limitEffectiveTo() {
            return limitRule.effectiveTo();
        }

        public LocalDate finalFormulaEffectiveFrom() {
            return finalFormulaRule.effectiveFrom();
        }

        public LocalDate finalFormulaEffectiveTo() {
            return finalFormulaRule.effectiveTo();
        }

        CreditBracket matchCreditBracket(long calculatedTaxAmount) {
            return creditBrackets.stream()
                .filter(bracket -> bracket.matches(calculatedTaxAmount))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No earned income tax credit bracket matches calculated tax amount " + calculatedTaxAmount + "."
                ));
        }

        LimitBracket matchLimitBracket(long totalGrossSalaryAmount) {
            return limitBrackets.stream()
                .filter(bracket -> bracket.matches(totalGrossSalaryAmount))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No earned income tax credit limit bracket matches gross salary amount " + totalGrossSalaryAmount + "."
                ));
        }
    }

    public record CreditBracket(
        Long sequence,
        Long fromExclusive,
        Long upToInclusive,
        long baseCreditAmount,
        long excessBaseAmount,
        BigDecimal excessCreditRate
    ) {

        boolean matches(long calculatedTaxAmount) {
            return (fromExclusive == null || calculatedTaxAmount > fromExclusive)
                && (upToInclusive == null || calculatedTaxAmount <= upToInclusive);
        }

        BigDecimal effectiveRate() {
            return excessCreditRate;
        }
    }

    public record LimitBracket(
        Long sequence,
        Long fromExclusive,
        Long upToInclusive,
        Long limitAmount,
        Long baseLimitAmount,
        Long reductionBaseAmount,
        BigDecimal reductionRate,
        BigDecimal reductionRatePercent,
        Long minimumLimitAmount
    ) {

        boolean matches(long totalGrossSalaryAmount) {
            return (fromExclusive == null || totalGrossSalaryAmount > fromExclusive)
                && (upToInclusive == null || totalGrossSalaryAmount <= upToInclusive);
        }

        BigDecimal effectiveReductionRate() {
            return reductionRate != null ? reductionRate : reductionRatePercent;
        }
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

    public enum FinalFormula {
        MIN_BASE_CREDIT_AND_SALARY_LIMIT;

        static FinalFormula from(String formula) {
            String normalizedFormula = formula.replaceAll("\\s+", "");
            if ("min(baseCreditAmount,salaryBasedLimitAmount)".equals(normalizedFormula)) {
                return MIN_BASE_CREDIT_AND_SALARY_LIMIT;
            }
            throw new IllegalStateException(FINAL_FORMULA_RULE_CODE + " has unsupported formula: " + formula);
        }

        long apply(long baseCreditAmount, long salaryBasedLimitAmount) {
            return Math.min(baseCreditAmount, salaryBasedLimitAmount);
        }
    }
}
