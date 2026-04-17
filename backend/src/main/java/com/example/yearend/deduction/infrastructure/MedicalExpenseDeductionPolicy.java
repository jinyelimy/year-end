package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.AbstractDeductionPolicy;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.domain.TaxContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 의료비 세액공제 (소득세법 제59조의4 제2항)
 *
 * subType별 공제율:
 *   MEDICAL_GENERAL, MEDICAL_TOTAL    : 15%, 한도 700만원
 *   MEDICAL_DISABILITY, MEDICAL_SENIOR, MEDICAL_SERIOUS_ILLNESS : 15%, 한도없음
 *   MEDICAL_INFERTILITY               : 30%, 한도없음
 *   MEDICAL_PREMATURE                 : 20%, 한도없음
 *
 * 3% 임계율(총급여 대비) 초과분에 공제율 적용.
 * 의료비는 세액공제이므로 소득공제(appliedAmount) 기여는 0.
 */
@Component
public class MedicalExpenseDeductionPolicy extends AbstractDeductionPolicy {

    public static final String THRESHOLD_RATE_RULE_CODE     = "MEDICAL_EXPENSE_THRESHOLD_RATE_2025";
    public static final String GENERAL_LIMIT_RATE_RULE_CODE = "MEDICAL_EXPENSE_GENERAL_LIMIT_RATE_2025";
    public static final String SPECIAL_RATE_RULE_CODE       = "MEDICAL_EXPENSE_SPECIAL_RATE_2025";
    public static final String INFERTILITY_RATE_RULE_CODE   = "MEDICAL_EXPENSE_INFERTILITY_RATE_2025";
    public static final String PREMATURE_RATE_RULE_CODE     = "MEDICAL_EXPENSE_PREMATURE_RATE_2025";
    public static final String AGGREGATE_FORMULA_RULE_CODE  = "MEDICAL_EXPENSE_AGGREGATE_FORMULA_2025";
    public static final String TRACE_RULE_CODE              = "MEDICAL_EXPENSE_TRACE_2025";

    private static final double DEFAULT_THRESHOLD_RATE  = 0.03;
    private static final double DEFAULT_CREDIT_RATE     = 0.15;
    private static final long   DEFAULT_LIMIT_AMOUNT    = 7_000_000L;

    private final ObjectMapper objectMapper;

    public MedicalExpenseDeductionPolicy(ObjectMapper objectMapper,
                                         MedicalExpenseThresholdChecker thresholdChecker) {
        super(List.of(thresholdChecker));
        this.objectMapper = objectMapper;
    }

    @Override
    public DeductionType supports() {
        return DeductionType.MEDICAL_EXPENSE;
    }

    /**
     * 의료비는 세액공제이므로 소득공제 기여분은 0.
     */
    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        return 0L;
    }

    /**
     * 세액공제 = (의료비 - 총급여 × 3%) × subType별 공제율, 한도 적용.
     */
    @Override
    protected long calculateTaxCredit(TaxContext context, DeductionItem item) {
        MedicalRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        double thresholdRate = resolveThresholdRate(context.ruleSetSnapshot());
        long threshold = Math.round(context.totalSalary() * thresholdRate);
        long eligibleBase = Math.max(0L, item.getAmount() - threshold);
        long cappedBase = params.hasLimit()
            ? Math.min(eligibleBase, params.limitAmount())
            : eligibleBase;
        return Math.round(cappedBase * params.creditRate());
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item,
                                    long eligibleAmount, long appliedAmount) {
        MedicalRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        double thresholdRate = resolveThresholdRate(context.ruleSetSnapshot());
        long threshold = Math.round(context.totalSalary() * thresholdRate);
        long eligibleBase = Math.max(0L, item.getAmount() - threshold);
        long cappedBase = params.hasLimit() ? Math.min(eligibleBase, params.limitAmount()) : eligibleBase;
        long taxCredit = Math.round(cappedBase * params.creditRate());
        String limitNote = params.hasLimit()
            ? String.format(", 한도 %,d원", params.limitAmount())
            : ", 한도없음";
        return String.format(
            "의료비 세액공제 적용 subType=%s: 납입액 %,d원, 임계값 %,d원, 공제대상 %,d원%s, 공제율 %.0f%%, 세액공제 %,d원",
            item.getSubType(), item.getAmount(), threshold, cappedBase, limitNote,
            params.creditRate() * 100, taxCredit
        );
    }

    private MedicalRuleParams resolveParams(RuleSetSnapshot snapshot, String subType) {
        String ruleCode = resolveRuleCode(subType);
        return snapshot.rulesFor(DeductionType.MEDICAL_EXPENSE).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .map(this::parseParams)
            .orElseGet(() -> defaultParams(subType));
    }

    private String resolveRuleCode(String subType) {
        if (subType == null) {
            return GENERAL_LIMIT_RATE_RULE_CODE;
        }
        return switch (subType) {
            case "MEDICAL_INFERTILITY"                              -> INFERTILITY_RATE_RULE_CODE;
            case "MEDICAL_PREMATURE"                               -> PREMATURE_RATE_RULE_CODE;
            case "MEDICAL_DISABILITY", "MEDICAL_SENIOR",
                 "MEDICAL_SERIOUS_ILLNESS"                         -> SPECIAL_RATE_RULE_CODE;
            default                                                 -> GENERAL_LIMIT_RATE_RULE_CODE;
        };
    }

    private MedicalRuleParams parseParams(DeductionRule rule) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
            double creditRate  = params.path("creditRate").asDouble(DEFAULT_CREDIT_RATE);
            boolean hasLimit   = params.path("hasLimit").asBoolean(true);
            long limitAmount   = params.path("limitAmount").asLong(DEFAULT_LIMIT_AMOUNT);
            return new MedicalRuleParams(creditRate, hasLimit, limitAmount);
        } catch (Exception exception) {
            return defaultParams(null);
        }
    }

    private MedicalRuleParams defaultParams(String subType) {
        if ("MEDICAL_INFERTILITY".equals(subType)) {
            return new MedicalRuleParams(0.30, false, DEFAULT_LIMIT_AMOUNT);
        }
        if ("MEDICAL_PREMATURE".equals(subType)) {
            return new MedicalRuleParams(0.20, false, DEFAULT_LIMIT_AMOUNT);
        }
        if ("MEDICAL_DISABILITY".equals(subType) || "MEDICAL_SENIOR".equals(subType)
                || "MEDICAL_SERIOUS_ILLNESS".equals(subType)) {
            return new MedicalRuleParams(0.15, false, DEFAULT_LIMIT_AMOUNT);
        }
        return new MedicalRuleParams(DEFAULT_CREDIT_RATE, true, DEFAULT_LIMIT_AMOUNT);
    }

    private double resolveThresholdRate(RuleSetSnapshot snapshot) {
        return snapshot.rulesFor(DeductionType.MEDICAL_EXPENSE).stream()
            .filter(rule -> THRESHOLD_RATE_RULE_CODE.equals(rule.getRuleCode()))
            .findFirst()
            .map(rule -> {
                try {
                    JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
                    return params.path("thresholdRate").asDouble(DEFAULT_THRESHOLD_RATE);
                } catch (Exception e) {
                    return DEFAULT_THRESHOLD_RATE;
                }
            })
            .orElse(DEFAULT_THRESHOLD_RATE);
    }

    private record MedicalRuleParams(double creditRate, boolean hasLimit, long limitAmount) {
    }
}
