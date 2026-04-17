package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 연금계좌 세액공제 (소득세법 제59조의4 제4항)
 *
 * 납입 한도:
 *   연금저축 개별 한도: 600만원
 *   연금저축 + IRP 합산 한도: 900만원
 *   ISA 만기 전환 추가 한도: 전환금액 × 10%, 최대 300만원
 *
 * 공제율:
 *   총급여 5,500만원 이하: 15%
 *   총급여 5,500만원 초과: 12%
 */
@Component
public class PensionAccountTaxCreditCalculator {

    public static final String LIMIT_RULE_CODE = "PENSION_ACCOUNT_LIMIT_2025";
    public static final String RATE_RULE_CODE  = "PENSION_ACCOUNT_RATE_2025";
    public static final String TRACE_RULE_CODE = "PENSION_ACCOUNT_TRACE_2025";

    private static final long   DEFAULT_PENSION_SAVINGS_LIMIT    = 6_000_000L;
    private static final long   DEFAULT_COMBINED_PENSION_LIMIT   = 9_000_000L;
    private static final double DEFAULT_ISA_ADDITIONAL_RATE      = 0.10;
    private static final long   DEFAULT_ISA_ADDITIONAL_MAX_LIMIT = 3_000_000L;
    private static final long   DEFAULT_GROSS_SALARY_THRESHOLD   = 55_000_000L;
    private static final double DEFAULT_HIGH_RATE                = 0.15;
    private static final double DEFAULT_LOW_RATE                 = 0.12;

    private final ObjectMapper objectMapper;

    public PensionAccountTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PensionAccountRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        List<DeductionRule> rules = ruleSetSnapshot.rulesFor(DeductionType.PENSION_ACCOUNT);

        DeductionRule limitRule = findRule(rules, LIMIT_RULE_CODE);
        DeductionRule rateRule  = findRule(rules, RATE_RULE_CODE);
        DeductionRule traceRule = findRule(rules, TRACE_RULE_CODE);

        JsonNode limitParams = readParameters(limitRule);
        JsonNode rateParams  = readParameters(rateRule);

        return new PensionAccountRuleSnapshot(
            limitParams.path("pensionSavingsLimit").asLong(DEFAULT_PENSION_SAVINGS_LIMIT),
            limitParams.path("combinedPensionLimit").asLong(DEFAULT_COMBINED_PENSION_LIMIT),
            limitParams.path("isaAdditionalRate").asDouble(DEFAULT_ISA_ADDITIONAL_RATE),
            limitParams.path("isaAdditionalMaxLimit").asLong(DEFAULT_ISA_ADDITIONAL_MAX_LIMIT),
            rateParams.path("grossSalaryThreshold").asLong(DEFAULT_GROSS_SALARY_THRESHOLD),
            rateParams.path("highRate").asDouble(DEFAULT_HIGH_RATE),
            rateParams.path("lowRate").asDouble(DEFAULT_LOW_RATE)
        );
    }

    public PensionAccountCalculation calculate(
        long pensionSavingsAmount,
        long irpAmount,
        long isaMaturityTransferAmount,
        long totalGrossSalaryAmount,
        PensionAccountRuleSnapshot ruleSnapshot
    ) {
        long applicablePensionSavings = Math.min(pensionSavingsAmount, ruleSnapshot.pensionSavingsLimit());
        long applicableCombined = Math.min(applicablePensionSavings + irpAmount, ruleSnapshot.combinedPensionLimit());
        long isaAdditional = Math.min(
            (long)(isaMaturityTransferAmount * ruleSnapshot.isaAdditionalRate()),
            ruleSnapshot.isaAdditionalMaxLimit()
        );
        long totalApplicable = applicableCombined + isaAdditional;

        double creditRate = totalGrossSalaryAmount <= ruleSnapshot.grossSalaryThreshold()
            ? ruleSnapshot.highRate()
            : ruleSnapshot.lowRate();

        long creditAmount = (long)(totalApplicable * creditRate);

        return new PensionAccountCalculation(
            pensionSavingsAmount,
            irpAmount,
            applicablePensionSavings,
            applicableCombined,
            isaMaturityTransferAmount,
            isaAdditional,
            totalApplicable,
            creditRate,
            creditAmount
        );
    }

    private DeductionRule findRule(List<DeductionRule> rules, String ruleCode) {
        return rules.stream()
            .filter(r -> ruleCode.equals(r.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required pension account ruleCode: " + ruleCode
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

    public record PensionAccountRuleSnapshot(
        long pensionSavingsLimit,
        long combinedPensionLimit,
        double isaAdditionalRate,
        long isaAdditionalMaxLimit,
        long grossSalaryThreshold,
        double highRate,
        double lowRate
    ) { }

    public record PensionAccountCalculation(
        long pensionSavingsAmount,
        long irpAmount,
        long applicablePensionSavingsAmount,
        long applicableCombinedAmount,
        long isaMaturityTransferAmount,
        long isaAdditionalAmount,
        long totalApplicableAmount,
        double creditRate,
        long totalCreditAmount
    ) { }
}
