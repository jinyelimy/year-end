package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EligibilityCheckResult;
import com.example.yearend.deduction.domain.EligibilityChecker;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

@Component
public class MedicalExpenseThresholdChecker implements EligibilityChecker {

    @Override
    public EligibilityCheckResult check(TaxContext context, DeductionItem item) {
        if (item.getAmount() == null || item.getAmount() <= 0L) {
            return EligibilityCheckResult.fail("Medical expense amount must be greater than zero.");
        }

        long threshold = Math.round(context.totalSalary() * 0.03);
        if (item.getAmount() <= threshold) {
            return EligibilityCheckResult.fail("Medical expense does not exceed the 3 percent salary threshold.");
        }

        return EligibilityCheckResult.pass("Medical expense exceeds the 3 percent salary threshold.");
    }
}
