package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.RelationType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

/**
 * 자녀세액공제 (소득세법 제59조의4 제1항)
 *
 * 기본 공제:
 *   8세 이상 기본공제 대상 자녀 1명: 15만원
 *   2명: 30만원
 *   3명 이상: 30만원 + (자녀수 - 2) × 30만원
 *
 * 출생·입양 공제 (당해 과세기간):
 *   첫째: 30만원 / 둘째: 50만원 / 셋째 이상: 70만원
 */
@Component
public class ChildTaxCreditCalculator {

    public static final String AMOUNT_RULE_CODE       = "CHILD_TAX_CREDIT_AMOUNT_2025";
    public static final String BIRTH_ADOPTION_RULE_CODE = "CHILD_TAX_CREDIT_BIRTH_ADOPTION_2025";
    public static final String TRACE_RULE_CODE        = "CHILD_TAX_CREDIT_TRACE_2025";

    private static final int  DEFAULT_MIN_AGE                   = 8;
    private static final long DEFAULT_FIRST_CHILD_AMOUNT        = 150_000L;
    private static final long DEFAULT_SECOND_CHILD_AMOUNT       = 300_000L;
    private static final long DEFAULT_ADDITIONAL_CHILD_AMOUNT   = 300_000L;
    private static final long DEFAULT_BIRTH_FIRST_AMOUNT        = 300_000L;
    private static final long DEFAULT_BIRTH_SECOND_AMOUNT       = 500_000L;
    private static final long DEFAULT_BIRTH_THIRD_OR_MORE_AMOUNT = 700_000L;

    private final ObjectMapper objectMapper;

    public ChildTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChildTaxCreditRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        List<DeductionRule> rules = ruleSetSnapshot.rulesFor(DeductionType.CHILD_TAX_CREDIT);

        DeductionRule amountRule = findRule(rules, AMOUNT_RULE_CODE);
        DeductionRule birthAdoptionRule = findRule(rules, BIRTH_ADOPTION_RULE_CODE);
        DeductionRule traceRule = findRule(rules, TRACE_RULE_CODE);

        JsonNode amountParams = readParameters(amountRule);
        JsonNode birthParams  = readParameters(birthAdoptionRule);

        return new ChildTaxCreditRuleSnapshot(
            amountParams.path("minAge").asInt(DEFAULT_MIN_AGE),
            amountParams.path("firstChildAmount").asLong(DEFAULT_FIRST_CHILD_AMOUNT),
            amountParams.path("secondChildAmount").asLong(DEFAULT_SECOND_CHILD_AMOUNT),
            amountParams.path("additionalChildAmount").asLong(DEFAULT_ADDITIONAL_CHILD_AMOUNT),
            birthParams.path("firstChildAmount").asLong(DEFAULT_BIRTH_FIRST_AMOUNT),
            birthParams.path("secondChildAmount").asLong(DEFAULT_BIRTH_SECOND_AMOUNT),
            birthParams.path("thirdOrMoreChildAmount").asLong(DEFAULT_BIRTH_THIRD_OR_MORE_AMOUNT)
        );
    }

    public ChildTaxCreditCalculation calculate(
        int taxYear,
        List<Dependent> dependents,
        Map<String, Object> basicInfoAttributes,
        ChildTaxCreditRuleSnapshot ruleSnapshot
    ) {
        long eligibleChildCount = dependents.stream()
            .filter(d -> d.getRelationType() == RelationType.CHILD)
            .filter(Dependent::isBasicDeductionTarget)
            .filter(d -> ageAtYearEnd(taxYear, d.getBirthDate()) >= ruleSnapshot.minAge())
            .count();

        long basicChildCreditAmount = computeBasicCredit(eligibleChildCount, ruleSnapshot);

        int birthAdoptionCount = parseBirthAdoptionCount(basicInfoAttributes);
        long birthAdoptionCreditAmount = computeBirthAdoptionCredit(birthAdoptionCount, ruleSnapshot);

        long totalCreditAmount = basicChildCreditAmount + birthAdoptionCreditAmount;

        return new ChildTaxCreditCalculation(
            eligibleChildCount,
            basicChildCreditAmount,
            birthAdoptionCount,
            birthAdoptionCreditAmount,
            totalCreditAmount
        );
    }

    private long computeBasicCredit(long count, ChildTaxCreditRuleSnapshot snap) {
        if (count == 0) return 0L;
        if (count == 1) return snap.firstChildAmount();
        if (count == 2) return snap.secondChildAmount();
        return snap.secondChildAmount() + (count - 2) * snap.additionalChildAmount();
    }

    private long computeBirthAdoptionCredit(int count, ChildTaxCreditRuleSnapshot snap) {
        if (count <= 0) return 0L;
        long total = 0L;
        for (int i = 1; i <= count; i++) {
            if (i == 1)       total += snap.birthFirstChildAmount();
            else if (i == 2)  total += snap.birthSecondChildAmount();
            else               total += snap.birthThirdOrMoreChildAmount();
        }
        return total;
    }

    private int parseBirthAdoptionCount(Map<String, Object> attributes) {
        Object value = attributes.get("birthAdoptionChildCount");
        if (value instanceof Number num) {
            return num.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try { return Integer.parseInt(str); } catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private DeductionRule findRule(List<DeductionRule> rules, String ruleCode) {
        return rules.stream()
            .filter(r -> ruleCode.equals(r.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required child tax credit ruleCode: " + ruleCode
            ));
    }

    private JsonNode readParameters(DeductionRule rule) {
        try {
            return objectMapper.readTree(rule.getParameterJsonb());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to parse parameterJsonb for ruleCode " + rule.getRuleCode(), e
            );
        }
    }

    private static long ageAtYearEnd(int taxYear, LocalDate birthDate) {
        if (birthDate == null) return 0L;
        return Period.between(birthDate, LocalDate.of(taxYear, 12, 31)).getYears();
    }

    public record ChildTaxCreditRuleSnapshot(
        int minAge,
        long firstChildAmount,
        long secondChildAmount,
        long additionalChildAmount,
        long birthFirstChildAmount,
        long birthSecondChildAmount,
        long birthThirdOrMoreChildAmount
    ) { }

    public record ChildTaxCreditCalculation(
        long eligibleChildCount,
        long basicChildCreditAmount,
        long birthAdoptionChildCount,
        long birthAdoptionCreditAmount,
        long totalCreditAmount
    ) { }
}
