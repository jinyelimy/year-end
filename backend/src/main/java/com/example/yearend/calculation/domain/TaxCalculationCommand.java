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
    long healthInsurancePremiumAmount,
    long employmentInsurancePremiumAmount,
    long creditCardAmount,
    long debitCardAmount,
    long cashReceiptAmount,
    long zeroPayAmount,
    long donationPoliticalAmount,
    long donationLegalAmount,
    long donationEmployeeStockAmount,
    long donationDesignatedAmount,
    long donationDesignatedReligiousAmount,
    long pensionSavingsAmount,
    long irpAmount,
    long isaMaturityTransferAmount,
    long annualRentAmount,
    long housingLoanBankRepaymentAmount,
    long housingLoanIndividualRepaymentAmount,
    long longTermMortgageInterestAmount,
    String longTermMortgageRepaymentType,
    long housingSavingsContributionAmount,
    long longTermCollectiveInvestmentContributionAmount,
    long youthLongTermCollectiveInvestmentContributionAmount,
    long smeMutualAidContributionAmount,
    long smeMutualAidIncomeBasisAmount,
    long ventureDirectInvestmentAmount,
    long ventureFundInvestmentAmount,
    long employeeStockOwnershipContributionAmount,
    boolean isVentureEmployerForEsop,
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
