package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.calculation.domain.EarnedIncomeDeductionCalculator.EarnedIncomeDeductionRuleSnapshot;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditCalculation;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditRuleSnapshot;
import com.example.yearend.calculation.domain.IncomeTaxRateTableCalculator.IncomeTaxCalculation;
import com.example.yearend.calculation.domain.PensionInsurancePremiumCalculator.PensionInsurancePremiumCalculation;
import com.example.yearend.calculation.domain.PensionInsurancePremiumCalculator.PensionInsurancePremiumRuleSnapshot;
import com.example.yearend.calculation.domain.SocialInsurancePremiumCalculator.SocialInsurancePremiumCalculation;
import com.example.yearend.calculation.domain.SocialInsurancePremiumCalculator.SocialInsurancePremiumRuleSnapshot;
import com.example.yearend.calculation.domain.CreditCardDeductionCalculator.CreditCardCalculation;
import com.example.yearend.calculation.domain.CreditCardDeductionCalculator.CreditCardRuleSnapshot;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionCalculation;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionRuleSnapshot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    private final EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator;
    private final PersonalDeductionCalculator personalDeductionCalculator;
    private final PensionInsurancePremiumCalculator pensionInsurancePremiumCalculator;
    private final SocialInsurancePremiumCalculator socialInsurancePremiumCalculator;
    private final CreditCardDeductionCalculator creditCardDeductionCalculator;
    private final IncomeTaxRateTableCalculator incomeTaxRateTableCalculator;
    private final EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator;

    public DefaultTaxCalculationService(
        EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator,
        PersonalDeductionCalculator personalDeductionCalculator,
        PensionInsurancePremiumCalculator pensionInsurancePremiumCalculator,
        SocialInsurancePremiumCalculator socialInsurancePremiumCalculator,
        CreditCardDeductionCalculator creditCardDeductionCalculator,
        IncomeTaxRateTableCalculator incomeTaxRateTableCalculator,
        EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator
    ) {
        this.earnedIncomeDeductionCalculator = earnedIncomeDeductionCalculator;
        this.personalDeductionCalculator = personalDeductionCalculator;
        this.pensionInsurancePremiumCalculator = pensionInsurancePremiumCalculator;
        this.socialInsurancePremiumCalculator = socialInsurancePremiumCalculator;
        this.creditCardDeductionCalculator = creditCardDeductionCalculator;
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
        PensionInsurancePremiumRuleSnapshot pensionRuleSnapshot =
            pensionInsurancePremiumCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        PensionInsurancePremiumCalculation pensionCalculation = pensionInsurancePremiumCalculator.calculate(
            command.publicPensionContributionAmount(),
            totalIncomeAmount,
            personalDeductionAmount,
            pensionRuleSnapshot
        );
        long pensionInsurancePremiumDeductionAmount = pensionCalculation.appliedAmount();
        SocialInsurancePremiumRuleSnapshot socialRuleSnapshot =
            socialInsurancePremiumCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        SocialInsurancePremiumCalculation socialCalculation = socialInsurancePremiumCalculator.calculate(
            command.healthInsurancePremiumAmount(),
            command.employmentInsurancePremiumAmount(),
            totalIncomeAmount,
            personalDeductionAmount + pensionInsurancePremiumDeductionAmount,
            socialRuleSnapshot
        );
        long socialInsurancePremiumDeductionAmount = socialCalculation.appliedAmount();
        CreditCardRuleSnapshot creditCardRuleSnapshot =
            creditCardDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        CreditCardCalculation creditCardCalculation = creditCardDeductionCalculator.calculate(
            command.creditCardAmount(),
            command.debitCardAmount(),
            command.cashReceiptAmount(),
            command.zeroPayAmount(),
            command.taxableSalaryAmount(),
            creditCardRuleSnapshot
        );
        long creditCardDeductionAmount = creditCardCalculation.appliedAmount();
        long totalDeductionAmount = itemDeductionAmount + personalDeductionAmount + pensionInsurancePremiumDeductionAmount + socialInsurancePremiumDeductionAmount + creditCardDeductionAmount;
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
        trace.add("ruleCode " + pensionRuleSnapshot.eligibilityRule().ruleCode() + " applied");
        trace.add(pensionRuleSnapshot.eligibilityRule().ruleCode() + " effectiveFrom = " + pensionRuleSnapshot.eligibilityRule().effectiveFrom());
        trace.add(pensionRuleSnapshot.eligibilityRule().ruleCode() + " effectiveTo = " + pensionRuleSnapshot.eligibilityRule().effectiveTo());
        trace.add("pensionEligiblePaidAmount = " + pensionCalculation.eligiblePaidAmount());
        trace.add("ruleCode " + pensionRuleSnapshot.paidAmountRule().ruleCode() + " applied");
        trace.add(pensionRuleSnapshot.paidAmountRule().ruleCode() + " effectiveFrom = " + pensionRuleSnapshot.paidAmountRule().effectiveFrom());
        trace.add(pensionRuleSnapshot.paidAmountRule().ruleCode() + " effectiveTo = " + pensionRuleSnapshot.paidAmountRule().effectiveTo());
        trace.add("ruleCode " + pensionRuleSnapshot.aggregateCapRule().ruleCode() + " applied");
        trace.add("pensionAggregateCapRemainingAmount = " + pensionCalculation.aggregateCapRemainingAmount());
        trace.add("ruleCode " + pensionRuleSnapshot.traceRule().ruleCode() + " applied");
        trace.add("pensionInsurancePremiumDeductionAmount = " + pensionInsurancePremiumDeductionAmount);
        trace.add("ruleCode " + socialRuleSnapshot.healthEligibilityRule().ruleCode() + " applied");
        trace.add(socialRuleSnapshot.healthEligibilityRule().ruleCode() + " effectiveFrom = " + socialRuleSnapshot.healthEligibilityRule().effectiveFrom());
        trace.add(socialRuleSnapshot.healthEligibilityRule().ruleCode() + " effectiveTo = " + socialRuleSnapshot.healthEligibilityRule().effectiveTo());
        trace.add("socialHealthInsurancePremiumAmount = " + socialCalculation.effectiveHealthAmount());
        trace.add("ruleCode " + socialRuleSnapshot.employmentEligibilityRule().ruleCode() + " applied");
        trace.add(socialRuleSnapshot.employmentEligibilityRule().ruleCode() + " effectiveFrom = " + socialRuleSnapshot.employmentEligibilityRule().effectiveFrom());
        trace.add(socialRuleSnapshot.employmentEligibilityRule().ruleCode() + " effectiveTo = " + socialRuleSnapshot.employmentEligibilityRule().effectiveTo());
        trace.add("socialEmploymentInsurancePremiumAmount = " + socialCalculation.effectiveEmploymentAmount());
        trace.add("ruleCode " + socialRuleSnapshot.aggregateCapRule().ruleCode() + " applied");
        trace.add("socialInsuranceAggregateCapRemainingAmount = " + socialCalculation.aggregateCapRemainingAmount());
        trace.add("ruleCode " + socialRuleSnapshot.traceRule().ruleCode() + " applied");
        trace.add("socialInsurancePremiumDeductionAmount = " + socialInsurancePremiumDeductionAmount);
        trace.add("ruleCode " + creditCardRuleSnapshot.minimumUsageThresholdRule().ruleCode() + " applied");
        trace.add("creditCardAmount = " + creditCardCalculation.creditCardAmount());
        trace.add("debitCardAmount = " + creditCardCalculation.debitCardAmount());
        trace.add("cashReceiptAmount = " + creditCardCalculation.cashReceiptAmount());
        trace.add("zeroPayAmount = " + creditCardCalculation.zeroPayAmount());
        trace.add("totalCreditCardUsageAmount = " + creditCardCalculation.totalUsageAmount());
        trace.add("minimumUsageThresholdAmount = " + creditCardCalculation.minimumUsageThresholdAmount());
        trace.add("ruleCode " + creditCardRuleSnapshot.rateCreditRule().ruleCode() + " applied");
        trace.add("creditCardAboveThresholdAmount = " + creditCardCalculation.creditCardAboveThresholdAmount());
        trace.add("ruleCode " + creditCardRuleSnapshot.rateDebitCashZeroPayRule().ruleCode() + " applied");
        trace.add("debitCashZeroPayAboveThresholdAmount = " + creditCardCalculation.debitCashZeroPayAboveThresholdAmount());
        trace.add("ruleCode " + creditCardRuleSnapshot.basicLimitRule().ruleCode() + " applied");
        trace.add("creditCardBasicLimitAmount = " + creditCardCalculation.basicLimitAmount());
        trace.add("ruleCode " + creditCardRuleSnapshot.traceRule().ruleCode() + " applied");
        trace.add("creditCardDeductionAmount = " + creditCardDeductionAmount);
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
            pensionInsurancePremiumDeductionAmount,
            socialInsurancePremiumDeductionAmount,
            creditCardDeductionAmount,
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
