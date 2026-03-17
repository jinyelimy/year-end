package com.example.yearend.deduction.domain;

public interface EligibilityChecker {

    EligibilityCheckResult check(TaxContext context, DeductionItem item);
}
