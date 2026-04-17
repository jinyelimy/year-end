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
import java.util.Locale;

/**
 * 교육비 세액공제 (소득세법 제59조의4 제3항)
 *
 * 공제율 15%, subType별 한도:
 *   SELF_EDUCATION      : 한도없음 (대학원 포함)
 *   UNIVERSITY          : 900만원
 *   PRESCHOOL, SCHOOL, ACADEMY : 300만원
 *   UNIFORM             : 50만원
 *
 * 교육비는 세액공제이므로 소득공제(appliedAmount) 기여는 0.
 */
@Component
public class EducationExpenseDeductionPolicy extends AbstractDeductionPolicy {

    public static final String CREDIT_RATE_RULE_CODE     = "EDUCATION_EXPENSE_CREDIT_RATE_2025";
    public static final String SELF_LIMIT_RULE_CODE      = "EDUCATION_EXPENSE_SELF_LIMIT_2025";
    public static final String UNIVERSITY_LIMIT_RULE_CODE = "EDUCATION_EXPENSE_UNIVERSITY_LIMIT_2025";
    public static final String SCHOOL_LIMIT_RULE_CODE    = "EDUCATION_EXPENSE_SCHOOL_LIMIT_2025";
    public static final String UNIFORM_LIMIT_RULE_CODE   = "EDUCATION_EXPENSE_UNIFORM_LIMIT_2025";
    public static final String AGGREGATE_FORMULA_RULE_CODE = "EDUCATION_EXPENSE_AGGREGATE_FORMULA_2025";
    public static final String TRACE_RULE_CODE           = "EDUCATION_EXPENSE_TRACE_2025";

    private static final double DEFAULT_CREDIT_RATE      = 0.15;
    private static final long   UNIVERSITY_DEFAULT_LIMIT = 9_000_000L;
    private static final long   SCHOOL_DEFAULT_LIMIT     = 3_000_000L;
    private static final long   UNIFORM_DEFAULT_LIMIT    = 500_000L;

    private final ObjectMapper objectMapper;

    public EducationExpenseDeductionPolicy(
        DependentIncomeEligibilityChecker dependentIncomeEligibilityChecker,
        EducationInstitutionChecker educationInstitutionChecker,
        ObjectMapper objectMapper
    ) {
        super(List.of(dependentIncomeEligibilityChecker, educationInstitutionChecker));
        this.objectMapper = objectMapper;
    }

    @Override
    public DeductionType supports() {
        return DeductionType.EDUCATION_EXPENSE;
    }

    /**
     * 교육비는 세액공제이므로 소득공제 기여분은 0.
     */
    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        return 0L;
    }

    /**
     * 세액공제 = min(amount, limit) × 15%
     */
    @Override
    protected long calculateTaxCredit(TaxContext context, DeductionItem item) {
        EducationRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        double creditRate = resolveCreditRate(context.ruleSetSnapshot());
        long cappedAmount = params.hasLimit()
            ? Math.min(item.getAmount(), params.limitAmount())
            : item.getAmount();
        return Math.round(cappedAmount * creditRate);
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item,
                                    long eligibleAmount, long appliedAmount) {
        EducationRuleParams params = resolveParams(context.ruleSetSnapshot(), item.getSubType());
        double creditRate = resolveCreditRate(context.ruleSetSnapshot());
        long cappedAmount = params.hasLimit()
            ? Math.min(item.getAmount(), params.limitAmount())
            : item.getAmount();
        long taxCredit = Math.round(cappedAmount * creditRate);
        String limitNote = params.hasLimit()
            ? String.format(", 한도 %,d원", params.limitAmount())
            : ", 한도없음";
        return String.format(
            "교육비 세액공제 적용 subType=%s: 납입액 %,d원, 한도적용 후 %,d원%s, 공제율 %.0f%%, 세액공제 %,d원",
            item.getSubType(), item.getAmount(), cappedAmount, limitNote,
            creditRate * 100, taxCredit
        );
    }

    private EducationRuleParams resolveParams(RuleSetSnapshot snapshot, String subType) {
        String ruleCode = resolveRuleCode(subType);
        return snapshot.rulesFor(DeductionType.EDUCATION_EXPENSE).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .map(this::parseParams)
            .orElseGet(() -> defaultParams(subType));
    }

    private String resolveRuleCode(String subType) {
        String normalized = subType == null ? "" : subType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SELF_EDUCATION" -> SELF_LIMIT_RULE_CODE;
            case "UNIVERSITY"     -> UNIVERSITY_LIMIT_RULE_CODE;
            case "UNIFORM"        -> UNIFORM_LIMIT_RULE_CODE;
            default               -> SCHOOL_LIMIT_RULE_CODE; // PRESCHOOL, SCHOOL, ACADEMY
        };
    }

    private EducationRuleParams parseParams(DeductionRule rule) {
        try {
            JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
            boolean hasLimit  = params.path("hasLimit").asBoolean(true);
            long limitAmount  = params.path("limitAmount").asLong(SCHOOL_DEFAULT_LIMIT);
            return new EducationRuleParams(hasLimit, limitAmount);
        } catch (Exception exception) {
            return defaultParams(null);
        }
    }

    private EducationRuleParams defaultParams(String subType) {
        String normalized = subType == null ? "" : subType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SELF_EDUCATION" -> new EducationRuleParams(false, Long.MAX_VALUE);
            case "UNIVERSITY"     -> new EducationRuleParams(true, UNIVERSITY_DEFAULT_LIMIT);
            case "UNIFORM"        -> new EducationRuleParams(true, UNIFORM_DEFAULT_LIMIT);
            default               -> new EducationRuleParams(true, SCHOOL_DEFAULT_LIMIT);
        };
    }

    private double resolveCreditRate(RuleSetSnapshot snapshot) {
        return snapshot.rulesFor(DeductionType.EDUCATION_EXPENSE).stream()
            .filter(rule -> CREDIT_RATE_RULE_CODE.equals(rule.getRuleCode()))
            .findFirst()
            .map(rule -> {
                try {
                    JsonNode params = objectMapper.readTree(rule.getParameterJsonb());
                    return params.path("creditRate").asDouble(DEFAULT_CREDIT_RATE);
                } catch (Exception e) {
                    return DEFAULT_CREDIT_RATE;
                }
            })
            .orElse(DEFAULT_CREDIT_RATE);
    }

    private record EducationRuleParams(boolean hasLimit, long limitAmount) {
    }
}
