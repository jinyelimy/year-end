package com.example.yearend.deduction.domain;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDeductionPolicy implements DeductionPolicy {

    private final List<EligibilityChecker> checkers;

    protected AbstractDeductionPolicy(List<EligibilityChecker> checkers) {
        this.checkers = checkers;
    }

    @Override
    public DeductionDecision evaluate(TaxContext context, DeductionItem item) {
        List<String> reasons = new ArrayList<>();

        for (EligibilityChecker checker : checkers) {
            EligibilityCheckResult result = checker.check(context, item);
            reasons.add(result.reason());
            if (!result.passed()) {
                return DeductionDecision.rejected(item.getId(), supports(), item.getAmount(), reasons);
            }
        }

        long eligibleAmount = calculateEligibleAmount(context, item);
        long appliedAmount = applyLimit(context, item, eligibleAmount);
        long taxCredit = calculateTaxCredit(context, item);
        reasons.add(explainApplied(context, item, eligibleAmount, appliedAmount));

        return new DeductionDecision(
            item.getId(),
            supports(),
            true,
            item.getAmount(),
            eligibleAmount,
            appliedAmount,
            taxCredit,
            reasons
        );
    }

    protected abstract long calculateEligibleAmount(TaxContext context, DeductionItem item);

    protected long applyLimit(TaxContext context, DeductionItem item, long eligibleAmount) {
        return eligibleAmount;
    }

    protected long calculateTaxCredit(TaxContext context, DeductionItem item) {
        return 0L;
    }

    protected abstract String explainApplied(TaxContext context, DeductionItem item, long eligibleAmount, long appliedAmount);
}
