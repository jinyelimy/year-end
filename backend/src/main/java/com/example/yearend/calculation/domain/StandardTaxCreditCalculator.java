package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 표준세액공제 (소득세법 제59조의4 제9항)
 *
 * 근로자가 특별소득공제, 특별세액공제, 월세액세액공제를 신청하지 않은 경우,
 * 연 13만원의 정액 세액공제 적용.
 * 실무상 계산 엔진은 특별세액공제 합계(보험료/의료비/교육비/기부금)와
 * 정액 13만원 중 큰 값을 자동 선택한다.
 */
@Component
public class StandardTaxCreditCalculator {

    public static final String FLAT_AMOUNT_RULE_CODE      = "STANDARD_TAX_CREDIT_FLAT_AMOUNT_2025";
    public static final String WORKER_ELIGIBILITY_RULE_CODE = "STANDARD_TAX_CREDIT_WORKER_ELIGIBILITY_2025";
    public static final String CHOICE_FORMULA_RULE_CODE    = "STANDARD_TAX_CREDIT_VS_SPECIAL_CHOICE_FORMULA_2025";

    private static final long DEFAULT_FLAT_AMOUNT = 130_000L;

    private final ObjectMapper objectMapper;

    public StandardTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StandardTaxCreditRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        List<DeductionRule> rules = ruleSetSnapshot.rulesFor(DeductionType.STANDARD_TAX_CREDIT);
        DeductionRule flatRule = findRule(rules, FLAT_AMOUNT_RULE_CODE);
        DeductionRule choiceRule = findRule(rules, CHOICE_FORMULA_RULE_CODE);

        JsonNode flatParams = readParameters(flatRule);
        JsonNode choiceParams = readParameters(choiceRule);

        long flatAmount = flatParams.path("amountPerPerson").asLong(DEFAULT_FLAT_AMOUNT);
        String tieBreak = choiceParams.path("tieBreakPreference").asText("STANDARD");

        return new StandardTaxCreditRuleSnapshot(flatAmount, tieBreak);
    }

    public StandardTaxCreditCalculation calculate(
        long specialTaxCreditTotal,
        boolean hasEarnedIncome,
        StandardTaxCreditRuleSnapshot snapshot
    ) {
        if (!hasEarnedIncome) {
            return new StandardTaxCreditCalculation(
                specialTaxCreditTotal,
                specialTaxCreditTotal,
                0L,
                "SPECIAL"
            );
        }

        long standardFlat = snapshot.flatAmount();
        boolean standardWins;
        if (standardFlat > specialTaxCreditTotal) {
            standardWins = true;
        } else if (standardFlat < specialTaxCreditTotal) {
            standardWins = false;
        } else {
            standardWins = "STANDARD".equalsIgnoreCase(snapshot.tieBreakPreference());
        }

        long appliedAmount = standardWins ? standardFlat : specialTaxCreditTotal;
        String chosenVariant = standardWins ? "STANDARD" : "SPECIAL";

        return new StandardTaxCreditCalculation(
            appliedAmount,
            specialTaxCreditTotal,
            standardFlat,
            chosenVariant
        );
    }

    private DeductionRule findRule(List<DeductionRule> rules, String ruleCode) {
        return rules.stream()
            .filter(r -> ruleCode.equals(r.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required standard tax credit ruleCode: " + ruleCode
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

    public record StandardTaxCreditRuleSnapshot(
        long flatAmount,
        String tieBreakPreference
    ) { }

    public record StandardTaxCreditCalculation(
        long appliedAmount,
        long specialTaxCreditTotal,
        long standardFlatAmount,
        String chosenVariant
    ) { }
}
