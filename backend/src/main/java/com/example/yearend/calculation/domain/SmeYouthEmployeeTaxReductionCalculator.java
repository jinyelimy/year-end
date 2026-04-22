package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 중소기업 취업자 소득세 감면 (조세특례제한법 제30조)
 *
 * 청년/60세 이상/장애인/경력단절여성이 중소기업에 취업하여 근로소득을 받는 경우,
 * 해당 근로소득에 대한 산출세액 배분액에 대상 유형별 감면율을 곱해 감면한다.
 * 감면액은 대상 유형별 연간 한도 (청년 200만원, 그 외 150만원) 를 초과하지 못한다.
 *
 * 본 슬라이스는 감면(減免) 이지만 코드 상으로는 세액공제와 같이 산출세액 이후 차감된다.
 * 자격 판정(연령/중소기업 해당 여부/감면 기간) 은 상위 레이어에서 수행하며,
 * Calculator 는 caller 가 결정한 category 를 그대로 사용해 감면액을 산출한다.
 *
 * 감면액 = min(산출세액 × (중소기업취업자근로소득 / 근로소득금액) × 감면율, 연간한도)
 */
@Component
public class SmeYouthEmployeeTaxReductionCalculator {

    public static final String RATE_RULE_CODE        = "SME_YOUTH_EMPLOYEE_TAX_REDUCTION_RATE_2025";
    public static final String LIMIT_RULE_CODE       = "SME_YOUTH_EMPLOYEE_TAX_REDUCTION_LIMIT_2025";
    public static final String ELIGIBILITY_RULE_CODE = "SME_YOUTH_EMPLOYEE_TAX_REDUCTION_ELIGIBILITY_2025";
    public static final String TRACE_RULE_CODE       = "SME_YOUTH_EMPLOYEE_TAX_REDUCTION_TRACE_2025";

    private final ObjectMapper objectMapper;

    public SmeYouthEmployeeTaxReductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SmeYouthEmployeeTaxReductionRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule rateRule        = findRule(ruleSetSnapshot, RATE_RULE_CODE);
        DeductionRule limitRule       = findRule(ruleSetSnapshot, LIMIT_RULE_CODE);
        DeductionRule eligibilityRule = findRule(ruleSetSnapshot, ELIGIBILITY_RULE_CODE);
        DeductionRule traceRule       = findRule(ruleSetSnapshot, TRACE_RULE_CODE);

        JsonNode rateParameters  = readParameters(rateRule);
        JsonNode limitParameters = readParameters(limitRule);
        JsonNode traceParameters = readParameters(traceRule);

        Map<String, Double> rateByCategory   = readDoubleMap(rateParameters, "rateByCategory");
        Map<String, Long> annualLimitByCategory = readLongMap(limitParameters, "annualLimitByCategory");
        List<String> traceFields              = readTraceFields(traceParameters);

        return new SmeYouthEmployeeTaxReductionRuleSnapshot(rateByCategory, annualLimitByCategory, traceFields);
    }

    public SmeYouthEmployeeTaxReductionCalculation calculate(
        String smeYouthCategory,
        long smeYouthEmployerIncomeAmount,
        long earnedIncomeAmount,
        long domesticCalculatedTaxAmount,
        SmeYouthEmployeeTaxReductionRuleSnapshot ruleSnapshot
    ) {
        long employerIncome  = Math.max(0L, smeYouthEmployerIncomeAmount);
        long earnedIncome    = Math.max(0L, earnedIncomeAmount);
        long calculatedTax   = Math.max(0L, domesticCalculatedTaxAmount);

        String category = smeYouthCategory == null ? "" : smeYouthCategory.trim();
        if (category.isEmpty() || !ruleSnapshot.rateByCategory().containsKey(category)) {
            return new SmeYouthEmployeeTaxReductionCalculation(
                category,
                employerIncome,
                0L,
                0.0,
                0L,
                0L
            );
        }

        if (earnedIncome <= 0L || employerIncome <= 0L || calculatedTax <= 0L) {
            double rateZero = ruleSnapshot.rateByCategory().get(category);
            long limitZero  = ruleSnapshot.annualLimitByCategory().getOrDefault(category, 0L);
            return new SmeYouthEmployeeTaxReductionCalculation(
                category,
                employerIncome,
                0L,
                rateZero,
                limitZero,
                0L
            );
        }

        long allocatedTax = Math.round(calculatedTax * ((double) employerIncome / earnedIncome));
        allocatedTax = Math.max(0L, allocatedTax);

        double reductionRate = ruleSnapshot.rateByCategory().get(category);
        long annualLimit     = ruleSnapshot.annualLimitByCategory().getOrDefault(category, 0L);

        long reductionBeforeLimit = Math.round(allocatedTax * reductionRate);
        reductionBeforeLimit = Math.max(0L, reductionBeforeLimit);

        long reductionAmount = Math.min(reductionBeforeLimit, annualLimit);
        reductionAmount = Math.max(0L, reductionAmount);

        return new SmeYouthEmployeeTaxReductionCalculation(
            category,
            employerIncome,
            allocatedTax,
            reductionRate,
            annualLimit,
            reductionAmount
        );
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.SME_YOUTH_EMPLOYEE_TAX_REDUCTION).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required sme youth employee tax reduction ruleCode: " + ruleCode
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

    private Map<String, Double> readDoubleMap(JsonNode parameters, String fieldName) {
        Map<String, Double> result = new LinkedHashMap<>();
        JsonNode node = parameters.get(fieldName);
        if (node == null || !node.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            result.put(entry.getKey(), entry.getValue().asDouble(0.0));
        }
        return result;
    }

    private Map<String, Long> readLongMap(JsonNode parameters, String fieldName) {
        Map<String, Long> result = new LinkedHashMap<>();
        JsonNode node = parameters.get(fieldName);
        if (node == null || !node.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            result.put(entry.getKey(), entry.getValue().asLong(0L));
        }
        return result;
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

    public record SmeYouthEmployeeTaxReductionRuleSnapshot(
        Map<String, Double> rateByCategory,
        Map<String, Long> annualLimitByCategory,
        List<String> traceFields
    ) {
        public SmeYouthEmployeeTaxReductionRuleSnapshot {
            rateByCategory        = Map.copyOf(rateByCategory);
            annualLimitByCategory = Map.copyOf(annualLimitByCategory);
            traceFields           = List.copyOf(traceFields);
        }
    }

    public record SmeYouthEmployeeTaxReductionCalculation(
        String smeYouthCategory,
        long smeYouthEmployerIncomeAmount,
        long smeYouthAllocatedTaxAmount,
        double smeYouthReductionRate,
        long smeYouthAnnualLimitAmount,
        long smeYouthEmployeeTaxReductionAmount
    ) {
    }
}
