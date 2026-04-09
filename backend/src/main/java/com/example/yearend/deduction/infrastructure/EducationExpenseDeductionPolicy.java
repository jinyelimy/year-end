package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.AbstractDeductionPolicy;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class EducationExpenseDeductionPolicy extends AbstractDeductionPolicy {

    public EducationExpenseDeductionPolicy(
        DependentIncomeEligibilityChecker dependentIncomeEligibilityChecker,
        EducationInstitutionChecker educationInstitutionChecker
    ) {
        super(List.of(dependentIncomeEligibilityChecker, educationInstitutionChecker));
    }

    @Override
    public DeductionType supports() {
        return DeductionType.EDUCATION_EXPENSE;
    }

    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        return item.getAmount();
    }

    @Override
    protected long applyLimit(TaxContext context, DeductionItem item, long eligibleAmount) {
        return Math.min(eligibleAmount, resolveLimit(item.getSubType()));
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item, long eligibleAmount, long appliedAmount) {
        return "Applied education expense limit for subtype " + item.getSubType() + ": " + appliedAmount;
    }

    private long resolveLimit(String subType) {
        String normalized = subType == null ? "" : subType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SELF_EDUCATION" -> Long.MAX_VALUE;
            case "UNIVERSITY" -> 9_000_000L;
            case "PRESCHOOL", "SCHOOL", "ACADEMY" -> 3_000_000L;
            default -> 0L;
        };
    }
}
