package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    private final EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator =
        new EarnedIncomeDeductionCalculator();

    @Override
    public TaxCalculationOutcome calculate(TaxCalculationCommand command) {
        long totalDeductionAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::appliedAmount)
            .sum();

        long earnedIncomeDeductionAmount = earnedIncomeDeductionCalculator.calculate(command.taxableSalaryAmount());
        long earnedIncomeAmount = Math.max(0L, command.taxableSalaryAmount() - earnedIncomeDeductionAmount);
        long totalIncomeAmount = earnedIncomeAmount + command.otherTaxableIncomeAmount();
        long taxableIncomeAmount = Math.max(0L, totalIncomeAmount - totalDeductionAmount);
        long calculatedTaxAmount = progressiveTax(taxableIncomeAmount);
        long taxCreditAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::taxCreditContribution)
            .sum();
        long finalTaxAmount = Math.max(0L, calculatedTaxAmount - taxCreditAmount);
        long expectedRefundAmount = command.withholdingTax() - finalTaxAmount;

        List<String> trace = new ArrayList<>();
        trace.add("ruleCode EMPLOYMENT_TAXABLE_SALARY_FORMULA applied");
        trace.add("totalGrossSalaryAmount = " + command.totalGrossSalaryAmount());
        trace.add("totalNonTaxableIncomeAmount = " + command.totalNonTaxableIncomeAmount());
        trace.add("taxableSalaryAmount = " + command.taxableSalaryAmount());
        trace.add("ruleCode EARNED_INCOME_DEDUCTION_BRACKETS applied");
        trace.add("ruleCode EARNED_INCOME_DEDUCTION_MAX_LIMIT applied");
        trace.add("earnedIncomeDeductionAmount = " + earnedIncomeDeductionAmount);
        trace.add("earnedIncomeAmount = " + earnedIncomeAmount);
        trace.add("otherTaxableIncomeAmount = " + command.otherTaxableIncomeAmount());
        trace.add("totalIncomeAmount = " + totalIncomeAmount);
        trace.add("totalDeductionAmount = " + totalDeductionAmount);
        trace.add("taxableIncomeAmount = " + taxableIncomeAmount);
        trace.add("calculatedTaxAmount = " + calculatedTaxAmount);
        trace.add("expectedRefundAmount = " + expectedRefundAmount);

        return new TaxCalculationOutcome(
            totalIncomeAmount,
            command.totalGrossSalaryAmount(),
            command.totalNonTaxableIncomeAmount(),
            command.taxableSalaryAmount(),
            command.otherTaxableIncomeAmount(),
            earnedIncomeDeductionAmount,
            earnedIncomeAmount,
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
