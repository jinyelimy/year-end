package com.example.yearend.deduction.domain;

import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.IncomeItem;

import java.util.List;
import java.util.Map;

public record TaxContext(
    int taxYear,
    long totalSalary,
    long withholdingTax,
    List<Dependent> dependents,
    List<IncomeItem> incomeItems,
    Map<String, Object> basicInfoAttributes,
    RuleSetSnapshot ruleSetSnapshot
) {

    public TaxContext {
        dependents = List.copyOf(dependents);
        incomeItems = List.copyOf(incomeItems);
        basicInfoAttributes = Map.copyOf(basicInfoAttributes);
    }

    public TaxContext(
        int taxYear,
        long totalSalary,
        long withholdingTax,
        List<Dependent> dependents,
        List<IncomeItem> incomeItems
    ) {
        this(taxYear, totalSalary, withholdingTax, dependents, incomeItems, Map.of(), RuleSetSnapshot.unresolved(taxYear));
    }
}
