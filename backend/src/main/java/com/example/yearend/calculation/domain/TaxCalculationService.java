package com.example.yearend.calculation.domain;

public interface TaxCalculationService {

    TaxCalculationOutcome calculate(TaxCalculationCommand command);
}
