package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;

import java.util.List;

public record TaxCalculationCommand(
    long totalSalary,
    long withholdingTax,
    List<DeductionDecision> deductionDecisions
) {
}
