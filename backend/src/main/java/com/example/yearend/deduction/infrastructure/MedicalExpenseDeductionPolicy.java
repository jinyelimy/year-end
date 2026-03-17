package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.AbstractDeductionPolicy;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.TaxContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MedicalExpenseDeductionPolicy extends AbstractDeductionPolicy {

    public MedicalExpenseDeductionPolicy(MedicalExpenseThresholdChecker medicalExpenseThresholdChecker) {
        super(List.of(medicalExpenseThresholdChecker));
    }

    @Override
    public DeductionType supports() {
        return DeductionType.MEDICAL_EXPENSE;
    }

    @Override
    protected long calculateEligibleAmount(TaxContext context, DeductionItem item) {
        long threshold = Math.round(context.totalSalary() * 0.03);
        return Math.max(0L, item.getAmount() - threshold);
    }

    @Override
    protected String explainApplied(TaxContext context, DeductionItem item, long eligibleAmount, long appliedAmount) {
        return "Applied medical expense amount after salary threshold adjustment: " + appliedAmount;
    }
}
