package com.example.yearend.calculation.api;

import com.example.yearend.deduction.domain.DeductionType;

import java.util.List;
import java.util.UUID;

public final class SimulationDtos {

    private SimulationDtos() {
    }

    public record DeductionDecisionResponse(
        UUID deductionItemId,
        DeductionType deductionType,
        boolean eligible,
        long requestedAmount,
        long eligibleAmount,
        long appliedAmount,
        List<String> reasons
    ) {
    }

    public record SimulationRunResponse(
        UUID sessionId,
        UUID calculationResultId,
        int calculationVersion,
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
        long totalDeductionAmount,
        long taxableIncomeAmount,
        long calculatedTaxAmount,
        long earnedIncomeTaxCreditAmount,
        long donationTaxCreditAmount,
        long childTaxCreditAmount,
        long pensionAccountTaxCreditAmount,
        long monthlyRentTaxCreditAmount,
        long otherTaxCreditAmount,
        long taxCreditAmount,
        long finalTaxAmount,
        long expectedRefundAmount,
        List<DeductionDecisionResponse> decisions,
        List<String> trace
    ) {
    }

    public record CalculationResultResponse(
        UUID resultId,
        int calculationVersion,
        String ruleVersion,
        String ruleSetId,
        String ruleSnapshotHash,
        long totalIncomeAmount,
        long totalDeductionAmount,
        long taxableIncomeAmount,
        long calculatedTaxAmount,
        long taxCreditAmount,
        long finalTaxAmount,
        long withholdingTaxAmount,
        long expectedRefundAmount,
        String decisionTraceJsonb,
        String summaryJsonb
    ) {
    }

    public record RejectionReasonResponse(
        UUID deductionItemId,
        DeductionType deductionType,
        List<String> reasons
    ) {
    }
}
