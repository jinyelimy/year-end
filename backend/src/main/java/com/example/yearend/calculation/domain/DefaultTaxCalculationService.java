package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    @Override
    public TaxCalculationOutcome calculate(TaxCalculationCommand command) {
        long totalDeductionAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::appliedAmount)
            .sum();

        long taxableIncomeAmount = Math.max(0L, command.totalSalary() - totalDeductionAmount);
        long calculatedTaxAmount = progressiveTax(taxableIncomeAmount);
        long taxCreditAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::taxCreditContribution)
            .sum();
        long finalTaxAmount = Math.max(0L, calculatedTaxAmount - taxCreditAmount);
        long expectedRefundAmount = command.withholdingTax() - finalTaxAmount;

        List<String> trace = new ArrayList<>();
        trace.add("totalIncomeAmount = " + command.totalSalary());
        trace.add("totalDeductionAmount = " + totalDeductionAmount);
        trace.add("taxableIncomeAmount = " + taxableIncomeAmount);
        trace.add("calculatedTaxAmount = " + calculatedTaxAmount);
        trace.add("expectedRefundAmount = " + expectedRefundAmount);

        return new TaxCalculationOutcome(
            command.totalSalary(),
            totalDeductionAmount,
            taxableIncomeAmount,
            calculatedTaxAmount,
            taxCreditAmount,
            finalTaxAmount,
            command.withholdingTax(),
            expectedRefundAmount,
            trace
        );
    }

    private long progressiveTax(long taxableIncomeAmount) {
        if (taxableIncomeAmount <= 14_000_000L) {
            return Math.round(taxableIncomeAmount * 0.06);
        }
        return Math.round(taxableIncomeAmount * 0.15);
    }
}
