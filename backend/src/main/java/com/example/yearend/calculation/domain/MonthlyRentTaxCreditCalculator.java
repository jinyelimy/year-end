package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 월세액 세액공제 (소득세법 제59조의4 제2항)
 *
 * 요건:
 *   무주택 세대주 또는 세대원, 총급여 80,000,000원 이하
 *   주택 규모: 국민주택규모(85㎡) 이하 또는 기준시가 4억 이하
 *
 * 한도: 연 12,000,000원
 *
 * 공제율:
 *   총급여 5,500만원 이하: 17%
 *   총급여 5,500만원 초과 ~ 8,000만원 이하: 15%
 *   총급여 8,000만원 초과: 공제 불가 (0원)
 */
@Component
public class MonthlyRentTaxCreditCalculator {

    public static final String LIMIT_RULE_CODE = "MONTHLY_RENT_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "MONTHLY_RENT_RATE_2025";
    public static final String TRACE_RULE_CODE = "MONTHLY_RENT_TRACE_2025";

    private static final long   DEFAULT_ANNUAL_RENT_LIMIT           = 12_000_000L;
    private static final long   DEFAULT_GROSS_SALARY_ELIGIBILITY    = 80_000_000L;
    private static final long   DEFAULT_GROSS_SALARY_THRESHOLD      = 55_000_000L;
    private static final double DEFAULT_HIGH_RATE                   = 0.17;
    private static final double DEFAULT_LOW_RATE                    = 0.15;

    private final ObjectMapper objectMapper;

    public MonthlyRentTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MonthlyRentRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        List<DeductionRule> rules = ruleSetSnapshot.rulesFor(DeductionType.MONTHLY_RENT);

        DeductionRule limitRule = findRule(rules, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(rules, RATE_RULE_CODE);
        DeductionRule traceRule = findRule(rules, TRACE_RULE_CODE);

        JsonNode limitParams = readParameters(limitRule);
        JsonNode rateParams  = readParameters(rateRule);

        return new MonthlyRentRuleSnapshot(
            limitParams.path("annualRentLimit").asLong(DEFAULT_ANNUAL_RENT_LIMIT),
            limitParams.path("grossSalaryEligibilityLimit").asLong(DEFAULT_GROSS_SALARY_ELIGIBILITY),
            rateParams.path("grossSalaryThreshold").asLong(DEFAULT_GROSS_SALARY_THRESHOLD),
            rateParams.path("highRate").asDouble(DEFAULT_HIGH_RATE),
            rateParams.path("lowRate").asDouble(DEFAULT_LOW_RATE)
        );
    }

    public MonthlyRentCalculation calculate(
        long annualRentAmount,
        long totalGrossSalaryAmount,
        MonthlyRentRuleSnapshot ruleSnapshot
    ) {
        if (totalGrossSalaryAmount > ruleSnapshot.grossSalaryEligibilityLimit()) {
            return new MonthlyRentCalculation(annualRentAmount, 0L, 0.0, 0L);
        }

        long eligibleRent = Math.min(annualRentAmount, ruleSnapshot.annualRentLimit());

        double creditRate = totalGrossSalaryAmount <= ruleSnapshot.grossSalaryThreshold()
            ? ruleSnapshot.highRate()
            : ruleSnapshot.lowRate();

        long creditAmount = (long)(eligibleRent * creditRate);

        return new MonthlyRentCalculation(annualRentAmount, eligibleRent, creditRate, creditAmount);
    }

    private DeductionRule findRule(List<DeductionRule> rules, String ruleCode) {
        return rules.stream()
            .filter(r -> ruleCode.equals(r.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required monthly rent ruleCode: " + ruleCode
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

    public record MonthlyRentRuleSnapshot(
        long annualRentLimit,
        long grossSalaryEligibilityLimit,
        long grossSalaryThreshold,
        double highRate,
        double lowRate
    ) { }

    public record MonthlyRentCalculation(
        long annualRentAmount,
        long eligibleRentAmount,
        double creditRate,
        long totalCreditAmount
    ) { }
}
