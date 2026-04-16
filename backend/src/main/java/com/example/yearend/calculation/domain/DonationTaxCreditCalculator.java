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
public class DonationTaxCreditCalculator {

    public static final String POLITICAL_BRACKET_RULE_CODE          = "DONATION_POLITICAL_BRACKET_2025";
    public static final String LEGAL_LIMIT_RATE_RULE_CODE           = "DONATION_LEGAL_LIMIT_RATE_2025";
    public static final String DESIGNATED_RELIGIOUS_RULE_CODE       = "DONATION_DESIGNATED_RELIGIOUS_LIMIT_RATE_2025";
    public static final String DESIGNATED_RULE_CODE                 = "DONATION_DESIGNATED_LIMIT_RATE_2025";
    public static final String EMPLOYEE_STOCK_RULE_CODE             = "DONATION_EMPLOYEE_STOCK_LIMIT_RATE_2025";
    public static final String AGGREGATE_FORMULA_RULE_CODE          = "DONATION_AGGREGATE_FORMULA_2025";
    public static final String TRACE_RULE_CODE                      = "DONATION_TRACE_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public DonationTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DonationRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule politicalRule      = findRule(ruleSetSnapshot, POLITICAL_BRACKET_RULE_CODE);
        DeductionRule legalRule          = findRule(ruleSetSnapshot, LEGAL_LIMIT_RATE_RULE_CODE);
        DeductionRule designatedRelRule  = findRule(ruleSetSnapshot, DESIGNATED_RELIGIOUS_RULE_CODE);
        DeductionRule designatedRule     = findRule(ruleSetSnapshot, DESIGNATED_RULE_CODE);
        DeductionRule employeeStockRule  = findRule(ruleSetSnapshot, EMPLOYEE_STOCK_RULE_CODE);
        DeductionRule aggregateRule      = findRule(ruleSetSnapshot, AGGREGATE_FORMULA_RULE_CODE);
        DeductionRule traceRule          = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode politicalParams     = readParameters(politicalRule);
        JsonNode legalParams         = readParameters(legalRule);
        JsonNode designatedRelParams = readParameters(designatedRelRule);
        JsonNode designatedParams    = readParameters(designatedRule);
        JsonNode employeeStockParams = readParameters(employeeStockRule);
        JsonNode traceParams         = readParameters(traceRule);

        return new DonationRuleSnapshot(
            RuleReference.from(politicalRule),
            politicalParams.path("firstThreshold").asLong(100_000L),
            politicalParams.path("firstRate").asDouble(0.9091),
            politicalParams.path("secondThreshold").asLong(30_000_000L),
            politicalParams.path("secondRate").asDouble(0.15),
            politicalParams.path("thirdRate").asDouble(0.25),
            RuleReference.from(legalRule),
            legalParams.path("incomeLimitRate").asDouble(1.0),
            legalParams.path("creditRateBelowThreshold").asDouble(0.15),
            legalParams.path("creditRateAboveThreshold").asDouble(0.30),
            legalParams.path("creditThresholdAmount").asLong(10_000_000L),
            RuleReference.from(designatedRelRule),
            designatedRelParams.path("incomeLimitRate").asDouble(0.10),
            RuleReference.from(designatedRule),
            designatedParams.path("incomeLimitRate").asDouble(0.30),
            RuleReference.from(employeeStockRule),
            employeeStockParams.path("incomeLimitRate").asDouble(0.30),
            employeeStockParams.path("creditRate").asDouble(0.30),
            RuleReference.from(aggregateRule),
            RuleReference.from(traceRule),
            readStringList(traceParams, "traceFields")
        );
    }

    public DonationCalculation calculate(
        long politicalAmount,
        long legalAmount,
        long employeeStockAmount,
        long designatedAmount,
        long designatedReligiousAmount,
        long earnedIncomeAmount,
        DonationRuleSnapshot snapshot
    ) {
        long effective = Math.max(0L, earnedIncomeAmount);

        // 1. 정치자금기부금 (no income limit)
        long effectivePolitical   = Math.max(0L, politicalAmount);
        long politicalCredit      = computePoliticalCredit(effectivePolitical, snapshot);

        // 2. 법정기부금 (limit: earnedIncome × 100%)
        long legalLimit           = Math.round(effective * snapshot.legalIncomeLimitRate());
        long effectiveLegal       = Math.min(Math.max(0L, legalAmount), legalLimit);
        long legalCredit          = computeTieredCredit(effectiveLegal, snapshot.creditThresholdAmount(),
                                        snapshot.creditRateBelowThreshold(), snapshot.creditRateAboveThreshold());

        // 3. 우리사주조합기부금 (limit: earnedIncome × 30%)
        long employeeStockLimit   = Math.round(effective * snapshot.employeeStockIncomeLimitRate());
        long effectiveEmployeeStock = Math.min(Math.max(0L, employeeStockAmount), employeeStockLimit);
        long employeeStockCredit  = Math.round(effectiveEmployeeStock * snapshot.employeeStockCreditRate());

        // 4. 지정기부금(비종교) (limit: (earnedIncome – effectiveLegal) × 30%)
        long residualForDesignated    = Math.max(0L, effective - effectiveLegal);
        long designatedLimit          = Math.round(residualForDesignated * snapshot.designatedIncomeLimitRate());
        long effectiveDesignated      = Math.min(Math.max(0L, designatedAmount), designatedLimit);
        long designatedCredit         = computeTieredCredit(effectiveDesignated, snapshot.creditThresholdAmount(),
                                            snapshot.creditRateBelowThreshold(), snapshot.creditRateAboveThreshold());

        // 5. 지정기부금(종교단체) (limit: (earnedIncome – effectiveLegal – effectiveDesignated) × 10%)
        long residualForReligious     = Math.max(0L, effective - effectiveLegal - effectiveDesignated);
        long designatedReligiousLimit = Math.round(residualForReligious * snapshot.designatedReligiousIncomeLimitRate());
        long effectiveDesignatedReligious = Math.min(Math.max(0L, designatedReligiousAmount), designatedReligiousLimit);
        long designatedReligiousCredit    = computeTieredCredit(effectiveDesignatedReligious, snapshot.creditThresholdAmount(),
                                               snapshot.creditRateBelowThreshold(), snapshot.creditRateAboveThreshold());

        long totalCredit = politicalCredit + legalCredit + employeeStockCredit + designatedCredit + designatedReligiousCredit;

        return new DonationCalculation(
            effectivePolitical,
            effectiveLegal,
            effectiveEmployeeStock,
            effectiveDesignated,
            effectiveDesignatedReligious,
            politicalCredit,
            legalCredit,
            employeeStockCredit,
            designatedCredit,
            designatedReligiousCredit,
            totalCredit,
            snapshot
        );
    }

    private long computePoliticalCredit(long amount, DonationRuleSnapshot snapshot) {
        if (amount == 0L) {
            return 0L;
        }
        long belowThreshold = Math.min(amount, snapshot.politicalFirstThreshold());
        long aboveThreshold = Math.max(0L, amount - snapshot.politicalFirstThreshold());

        long belowCredit = Math.round(belowThreshold * snapshot.politicalFirstRate());
        long aboveCredit;
        if (aboveThreshold <= snapshot.politicalSecondThreshold()) {
            aboveCredit = Math.round(aboveThreshold * snapshot.politicalSecondRate());
        } else {
            long secondPart = snapshot.politicalSecondThreshold();
            long thirdPart  = aboveThreshold - snapshot.politicalSecondThreshold();
            aboveCredit = Math.round(secondPart * snapshot.politicalSecondRate())
                        + Math.round(thirdPart  * snapshot.politicalThirdRate());
        }
        return belowCredit + aboveCredit;
    }

    private long computeTieredCredit(long amount, long threshold, double rateBelow, double rateAbove) {
        if (amount == 0L) {
            return 0L;
        }
        long below = Math.min(amount, threshold);
        long above = Math.max(0L, amount - threshold);
        return Math.round(below * rateBelow) + Math.round(above * rateAbove);
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.DONATION).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required donation tax credit ruleCode: " + ruleCode
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

    public record DonationRuleSnapshot(
        RuleReference politicalBracketRule,
        long politicalFirstThreshold,
        double politicalFirstRate,
        long politicalSecondThreshold,
        double politicalSecondRate,
        double politicalThirdRate,
        RuleReference legalLimitRateRule,
        double legalIncomeLimitRate,
        double creditRateBelowThreshold,
        double creditRateAboveThreshold,
        long creditThresholdAmount,
        RuleReference designatedReligiousRule,
        double designatedReligiousIncomeLimitRate,
        RuleReference designatedRule,
        double designatedIncomeLimitRate,
        RuleReference employeeStockRule,
        double employeeStockIncomeLimitRate,
        double employeeStockCreditRate,
        RuleReference aggregateFormulaRule,
        RuleReference traceRule,
        List<String> traceFields
    ) {
        public DonationRuleSnapshot {
            traceFields = List.copyOf(traceFields);
        }
    }

    public record DonationCalculation(
        long effectivePoliticalAmount,
        long effectiveLegalAmount,
        long effectiveEmployeeStockAmount,
        long effectiveDesignatedAmount,
        long effectiveDesignatedReligiousAmount,
        long politicalCreditAmount,
        long legalCreditAmount,
        long employeeStockCreditAmount,
        long designatedCreditAmount,
        long designatedReligiousCreditAmount,
        long totalCreditAmount,
        DonationRuleSnapshot ruleSnapshot
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
