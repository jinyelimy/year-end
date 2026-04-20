package com.example.yearend.calculation.domain;

import java.util.List;

public record TaxCalculationOutcome(
    long totalIncomeAmount,
    long totalGrossSalaryAmount,
    long totalNonTaxableIncomeAmount,
    long taxableSalaryAmount,
    long otherTaxableIncomeAmount,
    long earnedIncomeDeductionAmount,
    long earnedIncomeAmount,
    long personalDeductionAmount,
    long pensionInsurancePremiumDeductionAmount,
    long socialInsurancePremiumDeductionAmount,
    long creditCardDeductionAmount,
    long housingLoanDeductionAmount,
    long longTermMortgageDeductionAmount,
    long housingSavingsDeductionAmount,
    long longTermCollectiveInvestmentDeductionAmount,
    long youthLongTermCollectiveInvestmentDeductionAmount,
    long smeMutualAidDeductionAmount,
    long ventureInvestmentDeductionAmount,
    long employeeStockOwnershipDeductionAmount,
    long totalDeductionAmount,
    long taxableIncomeAmount,
    long calculatedTaxAmount,
    long earnedIncomeTaxCreditAmount,
    long donationTaxCreditAmount,
    long childTaxCreditAmount,
    long pensionAccountTaxCreditAmount,
    long monthlyRentTaxCreditAmount,
    long foreignTaxCreditAmount,
    long otherTaxCreditAmount,
    long taxCreditAmount,
    long finalTaxAmount,
    long withholdingTaxAmount,
    long expectedRefundAmount,
    List<String> trace
) {
}
