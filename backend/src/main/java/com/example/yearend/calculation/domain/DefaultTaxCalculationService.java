package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.calculation.domain.EarnedIncomeDeductionCalculator.EarnedIncomeDeductionRuleSnapshot;
import com.example.yearend.calculation.domain.IncomeTaxRateTableCalculator.IncomeTaxCalculation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    private final EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator;
    private final IncomeTaxRateTableCalculator incomeTaxRateTableCalculator;

    public DefaultTaxCalculationService(
        EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator,
        IncomeTaxRateTableCalculator incomeTaxRateTableCalculator
    ) {
        this.earnedIncomeDeductionCalculator = earnedIncomeDeductionCalculator;
        this.incomeTaxRateTableCalculator = incomeTaxRateTableCalculator;
    }

    @Override
    public TaxCalculationOutcome calculate(TaxCalculationCommand command) {
        long totalDeductionAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::appliedAmount)
            .sum();

        EarnedIncomeDeductionRuleSnapshot earnedIncomeRuleSnapshot =
            earnedIncomeDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        long earnedIncomeDeductionAmount = earnedIncomeDeductionCalculator.calculate(
            command.taxableSalaryAmount(),
            earnedIncomeRuleSnapshot
        );
        long earnedIncomeAmount = Math.max(0L, command.taxableSalaryAmount() - earnedIncomeDeductionAmount);
        long totalIncomeAmount = earnedIncomeAmount + command.otherTaxableIncomeAmount();
        long taxableIncomeAmount = Math.max(0L, totalIncomeAmount - totalDeductionAmount);
        IncomeTaxCalculation incomeTaxCalculation = incomeTaxRateTableCalculator.calculate(
            taxableIncomeAmount,
            command.ruleSetSnapshot()
        );
        long calculatedTaxAmount = incomeTaxCalculation.calculatedTaxAmount();
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
        trace.add("EARNED_INCOME_DEDUCTION_BRACKETS effectiveFrom = " + earnedIncomeRuleSnapshot.bracketsEffectiveFrom());
        trace.add("EARNED_INCOME_DEDUCTION_BRACKETS effectiveTo = " + earnedIncomeRuleSnapshot.bracketsEffectiveTo());
        trace.add("ruleCode EARNED_INCOME_DEDUCTION_MAX_LIMIT applied");
        trace.add("EARNED_INCOME_DEDUCTION_MAX_LIMIT effectiveFrom = " + earnedIncomeRuleSnapshot.maxLimitEffectiveFrom());
        trace.add("EARNED_INCOME_DEDUCTION_MAX_LIMIT effectiveTo = " + earnedIncomeRuleSnapshot.maxLimitEffectiveTo());
        trace.add("earnedIncomeDeductionAmount = " + earnedIncomeDeductionAmount);
        trace.add("earnedIncomeAmount = " + earnedIncomeAmount);
        trace.add("otherTaxableIncomeAmount = " + command.otherTaxableIncomeAmount());
        trace.add("totalIncomeAmount = " + totalIncomeAmount);
        trace.add("totalDeductionAmount = " + totalDeductionAmount);
        trace.add("ruleCode " + IncomeTaxRateTableCalculator.TAX_BASE_FORMULA_RULE_CODE + " applied");
        trace.add("taxableIncomeAmount = " + taxableIncomeAmount);
        trace.add("ruleCode " + IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE + " applied");
        trace.add(IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE + " effectiveFrom = " + incomeTaxCalculation.effectiveFrom());
        trace.add(IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE + " effectiveTo = " + incomeTaxCalculation.effectiveTo());
        trace.add("incomeTaxAppliedBracket = " + incomeTaxCalculation.appliedBracket().sequence());
        trace.add("incomeTaxRate = " + incomeTaxCalculation.appliedBracket().rate());
        trace.add("incomeTaxQuickDeductionAmount = " + incomeTaxCalculation.appliedBracket().quickDeductionAmount());
        trace.add("ruleCode " + IncomeTaxRateTableCalculator.CALCULATED_TAX_FORMULA_RULE_CODE + " applied");
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

}
