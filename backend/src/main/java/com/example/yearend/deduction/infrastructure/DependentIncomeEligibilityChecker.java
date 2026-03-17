package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EligibilityCheckResult;
import com.example.yearend.deduction.domain.EligibilityChecker;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

@Component
public class DependentIncomeEligibilityChecker implements EligibilityChecker {

    private static final long DEPENDENT_INCOME_LIMIT = 1_000_000L;

    @Override
    public EligibilityCheckResult check(TaxContext context, DeductionItem item) {
        if (item.getDependent() == null) {
            return EligibilityCheckResult.pass("No dependent is linked, so the item is treated as self expense.");
        }

        return context.dependents().stream()
            .filter(dependent -> dependent.getId().equals(item.getDependent().getId()))
            .findFirst()
            .map(dependent -> dependent.getAnnualIncomeAmount() <= DEPENDENT_INCOME_LIMIT
                ? EligibilityCheckResult.pass("Dependent income is within the basic deduction limit.")
                : EligibilityCheckResult.fail("Dependent income exceeds the basic deduction limit."))
            .orElseGet(() -> EligibilityCheckResult.fail("Dependent snapshot could not be found in the current tax session."));
    }
}
