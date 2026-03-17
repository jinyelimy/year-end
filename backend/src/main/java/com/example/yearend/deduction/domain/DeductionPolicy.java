package com.example.yearend.deduction.domain;

public interface DeductionPolicy {

    DeductionType supports();

    DeductionDecision evaluate(TaxContext context, DeductionItem item);
}
