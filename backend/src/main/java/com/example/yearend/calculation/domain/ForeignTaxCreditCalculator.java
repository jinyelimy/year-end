package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 외국납부세액공제 (소득세법 제57조)
 *
 * 거주자의 종합소득금액에 국외원천소득이 포함된 경우, 해당 국외원천소득에 대해
 * 외국정부에 납부한 세액을 산출세액에서 공제한다.
 *
 * 공제 한도 = 산출세액 × (국외원천소득금액 / 종합소득금액)
 * 공제액 = min(외국납부세액, 공제 한도)
 */
@Component
public class ForeignTaxCreditCalculator {

    public static final String RATE_RULE_CODE          = "FOREIGN_TAX_CREDIT_RATE_2025";
    public static final String LIMIT_FORMULA_RULE_CODE = "FOREIGN_TAX_CREDIT_LIMIT_FORMULA_2025";
    public static final String TRACE_RULE_CODE         = "FOREIGN_TAX_CREDIT_TRACE_2025";

    private final ObjectMapper objectMapper;

    public ForeignTaxCreditCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ForeignTaxCreditRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule rateRule  = findRule(ruleSetSnapshot, RATE_RULE_CODE);
        DeductionRule limitRule = findRule(ruleSetSnapshot, LIMIT_FORMULA_RULE_CODE);
        DeductionRule traceRule = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode rateParameters  = readParameters(rateRule);
        // limitRule 파라미터는 문서화 목적으로만 조회 — 계산 로직은 Calculator 내부 고정.
        readParameters(limitRule);
        JsonNode traceParameters = readParameters(traceRule);

        double creditRate        = rateParameters.path("creditRate").asDouble(1.00);
        List<String> traceFields = readTraceFields(traceParameters);

        return new ForeignTaxCreditRuleSnapshot(creditRate, traceFields);
    }

    public ForeignTaxCreditCalculation calculate(
        long foreignTaxPaidAmount,
        long foreignSourceIncomeAmount,
        long comprehensiveIncomeAmount,
        long domesticCalculatedTaxAmount,
        ForeignTaxCreditRuleSnapshot ruleSnapshot
    ) {
        long paidAmount = Math.max(0L, foreignTaxPaidAmount);
        long foreignSource = Math.max(0L, foreignSourceIncomeAmount);
        long comprehensive = Math.max(0L, comprehensiveIncomeAmount);
        long calculatedTax = Math.max(0L, domesticCalculatedTaxAmount);

        long limitAmount;
        if (comprehensive <= 0L) {
            limitAmount = 0L;
        } else {
            limitAmount = Math.round(calculatedTax * ((double) foreignSource / comprehensive));
        }
        limitAmount = Math.max(0L, limitAmount);

        long creditableAmount = Math.round(paidAmount * ruleSnapshot.creditRate());
        creditableAmount = Math.max(0L, creditableAmount);

        long foreignTaxCreditAmount = Math.min(creditableAmount, limitAmount);
        foreignTaxCreditAmount = Math.max(0L, foreignTaxCreditAmount);

        return new ForeignTaxCreditCalculation(
            paidAmount,
            foreignSource,
            comprehensive,
            calculatedTax,
            limitAmount,
            foreignTaxCreditAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.FOREIGN_TAX_CREDIT).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required foreign tax credit ruleCode: " + ruleCode
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

    private List<String> readTraceFields(JsonNode parameters) {
        List<String> fields = new ArrayList<>();
        JsonNode traceFieldsNode = parameters.get("traceFields");
        if (traceFieldsNode == null || !traceFieldsNode.isArray()) {
            return fields;
        }
        for (JsonNode fieldNode : traceFieldsNode) {
            fields.add(fieldNode.asText());
        }
        return fields;
    }

    public record ForeignTaxCreditRuleSnapshot(
        double creditRate,
        List<String> traceFields
    ) {
        public ForeignTaxCreditRuleSnapshot {
            traceFields = List.copyOf(traceFields);
        }
    }

    public record ForeignTaxCreditCalculation(
        long foreignTaxPaidAmount,
        long foreignSourceIncomeAmount,
        long comprehensiveIncomeAmount,
        long domesticCalculatedTaxAmount,
        long foreignTaxCreditLimitAmount,
        long foreignTaxCreditAmount
    ) {
    }
}
