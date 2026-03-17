package com.example.yearend.deduction.domain;

import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.IncomeItem;

import java.util.List;

public record TaxContext(
    int taxYear,
    long totalSalary,
    long withholdingTax,
    List<Dependent> dependents,
    List<IncomeItem> incomeItems
) {
}
