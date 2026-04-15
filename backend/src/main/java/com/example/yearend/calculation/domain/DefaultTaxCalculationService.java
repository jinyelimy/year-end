package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.calculation.domain.EarnedIncomeDeductionCalculator.EarnedIncomeDeductionRuleSnapshot;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditCalculation;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditRuleSnapshot;
import com.example.yearend.calculation.domain.IncomeTaxRateTableCalculator.IncomeTaxCalculation;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionCalculation;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionRuleSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    private final EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator;
    private final PersonalDeductionCalculator personalDeductionCalculator;
    private final IncomeTaxRateTableCalculator incomeTaxRateTableCalculator;
    private final EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator;

    public DefaultTaxCalculationService(
        EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator,
        PersonalDeductionCalculator personalDeductionCalculator,
        IncomeTaxRateTableCalculator incomeTaxRateTableCalculator,
        EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator
    ) {
        this.earnedIncomeDeductionCalculator = earnedIncomeDeductionCalculator;
        this.personalDeductionCalculator = personalDeductionCalculator;
        this.incomeTaxRateTableCalculator = incomeTaxRateTableCalculator;
        this.earnedIncomeTaxCreditCalculator = earnedIncomeTaxCreditCalculator;
    }

    @Override
    public TaxCalculationOutcome calculate(TaxCalculationCommand command) {
        long itemDeductionAmount = command.deductionDecisions().stream()
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
        PersonalDeductionRuleSnapshot personalDeductionRuleSnapshot =
            personalDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        PersonalDeductionCalculation personalDeductionCalculation = personalDeductionCalculator.calculate(
            command.taxYear(),
            totalIncomeAmount,
            command.dependents(),
            command.basicInfoAttributes(),
            personalDeductionRuleSnapshot
        );
        long personalDeductionAmount = personalDeductionCalculation.totalPersonalDeductionAmount();
        long totalDeductionAmount = itemDeductionAmount + personalDeductionAmount;
        long taxableIncomeAmount = Math.max(0L, totalIncomeAmount - totalDeductionAmount);
        IncomeTaxCalculation incomeTaxCalculation = incomeTaxRateTableCalculator.calculate(
            taxableIncomeAmount,
            command.ruleSetSnapshot()
        );
        long calculatedTaxAmount = incomeTaxCalculation.calculatedTaxAmount();
        EarnedIncomeTaxCreditRuleSnapshot earnedIncomeTaxCreditRuleSnapshot =
            earnedIncomeTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        EarnedIncomeTaxCreditCalculation earnedIncomeTaxCreditCalculation = earnedIncomeTaxCreditCalculator.calculate(
            command.totalGrossSalaryAmount(),
            calculatedTaxAmount,
            earnedIncomeTaxCreditRuleSnapshot
        );
        long earnedIncomeTaxCreditAmount = earnedIncomeTaxCreditCalculation.earnedIncomeTaxCreditAmount();
        long otherTaxCreditAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .mapToLong(DeductionDecision::taxCreditContribution)
            .sum();
        long taxCreditAmount = earnedIncomeTaxCreditAmount + otherTaxCreditAmount;
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
        trace.add("deductionItemAppliedAmount = " + itemDeductionAmount);
        trace.add("ruleCode " + personalDeductionRuleSnapshot.basicAmountRule().ruleCode() + " applied");
        trace.add(personalDeductionRuleSnapshot.basicAmountRule().ruleCode() + " effectiveFrom = " + personalDeductionRuleSnapshot.basicAmountRule().effectiveFrom());
        trace.add(personalDeductionRuleSnapshot.basicAmountRule().ruleCode() + " effectiveTo = " + personalDeductionRuleSnapshot.basicAmountRule().effectiveTo());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.basicEligibilityRule().ruleCode() + " applied");
        trace.add("personalBasicDeductionTargetCount = " + personalDeductionCalculation.basicTargetCount());
        trace.add("personalBasicDeductionAmount = " + personalDeductionCalculation.basicDeductionAmount());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.seniorRule().ruleCode() + " applied");
        trace.add("personalSeniorDeductionTargetCount = " + personalDeductionCalculation.seniorTargetCount());
        trace.add("personalSeniorDeductionAmount = " + personalDeductionCalculation.seniorDeductionAmount());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.disabledRule().ruleCode() + " applied");
        trace.add("personalDisabledDeductionTargetCount = " + personalDeductionCalculation.disabledTargetCount());
        trace.add("personalDisabledDeductionAmount = " + personalDeductionCalculation.disabledDeductionAmount());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.womanRule().ruleCode() + " applied");
        trace.add("personalWomanDeductionApplied = " + personalDeductionCalculation.womanDeductionApplied());
        trace.add("personalWomanDeductionAmount = " + personalDeductionCalculation.womanDeductionAmount());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.singleParentRule().ruleCode() + " applied");
        trace.add("personalSingleParentDeductionApplied = " + personalDeductionCalculation.singleParentDeductionApplied());
        trace.add("personalSingleParentDeductionAmount = " + personalDeductionCalculation.singleParentDeductionAmount());
        trace.add("ruleCode " + personalDeductionRuleSnapshot.aggregationRule().ruleCode() + " applied");
        trace.add("personalDeductionAmount = " + personalDeductionAmount);
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
        trace.add("ruleCode " + earnedIncomeTaxCreditRuleSnapshot.baseFormulaRule().ruleCode() + " applied");
        trace.add(earnedIncomeTaxCreditRuleSnapshot.baseFormulaRule().ruleCode() + " effectiveFrom = " + earnedIncomeTaxCreditRuleSnapshot.baseFormulaEffectiveFrom());
        trace.add(earnedIncomeTaxCreditRuleSnapshot.baseFormulaRule().ruleCode() + " effectiveTo = " + earnedIncomeTaxCreditRuleSnapshot.baseFormulaEffectiveTo());
        trace.add("baseEarnedIncomeTaxCreditAmount = " + earnedIncomeTaxCreditCalculation.baseCreditAmount());
        trace.add("ruleCode " + earnedIncomeTaxCreditRuleSnapshot.limitRule().ruleCode() + " applied");
        trace.add(earnedIncomeTaxCreditRuleSnapshot.limitRule().ruleCode() + " effectiveFrom = " + earnedIncomeTaxCreditRuleSnapshot.limitEffectiveFrom());
        trace.add(earnedIncomeTaxCreditRuleSnapshot.limitRule().ruleCode() + " effectiveTo = " + earnedIncomeTaxCreditRuleSnapshot.limitEffectiveTo());
        trace.add("earnedIncomeTaxCreditLimitAmount = " + earnedIncomeTaxCreditCalculation.salaryBasedLimitAmount());
        trace.add("ruleCode " + earnedIncomeTaxCreditRuleSnapshot.finalFormulaRule().ruleCode() + " applied");
        trace.add(earnedIncomeTaxCreditRuleSnapshot.finalFormulaRule().ruleCode() + " effectiveFrom = " + earnedIncomeTaxCreditRuleSnapshot.finalFormulaEffectiveFrom());
        trace.add(earnedIncomeTaxCreditRuleSnapshot.finalFormulaRule().ruleCode() + " effectiveTo = " + earnedIncomeTaxCreditRuleSnapshot.finalFormulaEffectiveTo());
        trace.add("earnedIncomeTaxCreditAmount = " + earnedIncomeTaxCreditAmount);
        trace.add("otherTaxCreditAmount = " + otherTaxCreditAmount);
        trace.add("taxCreditAmount = " + taxCreditAmount);
        trace.add("expectedRefundAmount = " + expectedRefundAmount);

        return new TaxCalculationOutcome(
            totalIncomeAmount,
            command.totalGrossSalaryAmount(),
            command.totalNonTaxableIncomeAmount(),
            command.taxableSalaryAmount(),
            command.otherTaxableIncomeAmount(),
            earnedIncomeDeductionAmount,
            earnedIncomeAmount,
            personalDeductionAmount,
            totalDeductionAmount,
            taxableIncomeAmount,
            calculatedTaxAmount,
            earnedIncomeTaxCreditAmount,
            otherTaxCreditAmount,
            taxCreditAmount,
            finalTaxAmount,
            command.withholdingTax(),
            expectedRefundAmount,
            trace
        );
    }

}
