package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.taxsession.domain.Dependent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TaxCalculationCommand(
    int taxYear,
    long totalGrossSalaryAmount,
    long totalNonTaxableIncomeAmount,
    long taxableSalaryAmount,
    long otherTaxableIncomeAmount,
    long withholdingTax,
    long publicPensionContributionAmount,
    List<Dependent> dependents,
    Map<String, Object> basicInfoAttributes,
    List<DeductionDecision> deductionDecisions,
    RuleSetSnapshot ruleSetSnapshot
) {
    public TaxCalculationCommand {
        dependents = List.copyOf(dependents);
        basicInfoAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(basicInfoAttributes));
        deductionDecisions = List.copyOf(deductionDecisions);
    }
}
