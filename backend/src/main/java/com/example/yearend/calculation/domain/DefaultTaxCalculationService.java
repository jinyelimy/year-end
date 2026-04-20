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
import com.example.yearend.calculation.domain.DonationTaxCreditCalculator.DonationCalculation;
import com.example.yearend.calculation.domain.DonationTaxCreditCalculator.DonationRuleSnapshot;
import com.example.yearend.calculation.domain.ChildTaxCreditCalculator.ChildTaxCreditCalculation;
import com.example.yearend.calculation.domain.ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot;
import com.example.yearend.calculation.domain.PensionAccountTaxCreditCalculator.PensionAccountCalculation;
import com.example.yearend.calculation.domain.PensionAccountTaxCreditCalculator.PensionAccountRuleSnapshot;
import com.example.yearend.calculation.domain.MonthlyRentTaxCreditCalculator.MonthlyRentCalculation;
import com.example.yearend.calculation.domain.MonthlyRentTaxCreditCalculator.MonthlyRentRuleSnapshot;
import com.example.yearend.calculation.domain.HousingLoanDeductionCalculator.HousingLoanCalculation;
import com.example.yearend.calculation.domain.HousingLoanDeductionCalculator.HousingLoanRuleSnapshot;
import com.example.yearend.calculation.domain.LongTermMortgageDeductionCalculator.LongTermMortgageCalculation;
import com.example.yearend.calculation.domain.LongTermMortgageDeductionCalculator.LongTermMortgageRuleSnapshot;
import com.example.yearend.calculation.domain.HousingSavingsDeductionCalculator.HousingSavingsCalculation;
import com.example.yearend.calculation.domain.HousingSavingsDeductionCalculator.HousingSavingsRuleSnapshot;
import com.example.yearend.calculation.domain.LongTermCollectiveInvestmentDeductionCalculator.LongTermCollectiveInvestmentCalculation;
import com.example.yearend.calculation.domain.LongTermCollectiveInvestmentDeductionCalculator.LongTermCollectiveInvestmentRuleSnapshot;
import com.example.yearend.calculation.domain.YouthLongTermCollectiveInvestmentDeductionCalculator.YouthLongTermCollectiveInvestmentCalculation;
import com.example.yearend.calculation.domain.YouthLongTermCollectiveInvestmentDeductionCalculator.YouthLongTermCollectiveInvestmentRuleSnapshot;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionCalculation;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionRuleSnapshot;
import com.example.yearend.calculation.domain.StandardTaxCreditCalculator.StandardTaxCreditCalculation;
import com.example.yearend.calculation.domain.StandardTaxCreditCalculator.StandardTaxCreditRuleSnapshot;
import com.example.yearend.deduction.domain.DeductionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTaxCalculationService implements TaxCalculationService {

    private static final String CHILD_TAX_CREDIT_AMOUNT_CODE        = ChildTaxCreditCalculator.AMOUNT_RULE_CODE;
    private static final String CHILD_TAX_CREDIT_BIRTH_ADOPTION_CODE = ChildTaxCreditCalculator.BIRTH_ADOPTION_RULE_CODE;
    private static final String PENSION_ACCOUNT_LIMIT_CODE           = PensionAccountTaxCreditCalculator.LIMIT_RULE_CODE;
    private static final String PENSION_ACCOUNT_RATE_CODE            = PensionAccountTaxCreditCalculator.RATE_RULE_CODE;
    private static final String MONTHLY_RENT_LIMIT_CODE              = MonthlyRentTaxCreditCalculator.LIMIT_RULE_CODE;
    private static final String MONTHLY_RENT_RATE_CODE               = MonthlyRentTaxCreditCalculator.RATE_RULE_CODE;
    private static final String HOUSING_LOAN_LIMIT_CODE              = HousingLoanDeductionCalculator.LIMIT_RULE_CODE;
    private static final String HOUSING_LOAN_RATE_CODE               = HousingLoanDeductionCalculator.RATE_RULE_CODE;
    private static final String LONG_TERM_MORTGAGE_LIMIT_CODE        = LongTermMortgageDeductionCalculator.LIMIT_RULE_CODE;
    private static final String HOUSING_SAVINGS_LIMIT_CODE           = HousingSavingsDeductionCalculator.LIMIT_RULE_CODE;
    private static final String HOUSING_SAVINGS_RATE_CODE            = HousingSavingsDeductionCalculator.RATE_RULE_CODE;
    private static final String LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_CODE = LongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE;
    private static final String LONG_TERM_COLLECTIVE_INVESTMENT_RATE_CODE  = LongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE;
    private static final String YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_CODE = YouthLongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE;
    private static final String YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_RATE_CODE  = YouthLongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE;

    private final EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator;
    private final PersonalDeductionCalculator personalDeductionCalculator;
    private final PensionInsurancePremiumCalculator pensionInsurancePremiumCalculator;
    private final SocialInsurancePremiumCalculator socialInsurancePremiumCalculator;
    private final CreditCardDeductionCalculator creditCardDeductionCalculator;
    private final DonationTaxCreditCalculator donationTaxCreditCalculator;
    private final ChildTaxCreditCalculator childTaxCreditCalculator;
    private final PensionAccountTaxCreditCalculator pensionAccountTaxCreditCalculator;
    private final MonthlyRentTaxCreditCalculator monthlyRentTaxCreditCalculator;
    private final HousingLoanDeductionCalculator housingLoanDeductionCalculator;
    private final LongTermMortgageDeductionCalculator longTermMortgageDeductionCalculator;
    private final HousingSavingsDeductionCalculator housingSavingsDeductionCalculator;
    private final LongTermCollectiveInvestmentDeductionCalculator longTermCollectiveInvestmentDeductionCalculator;
    private final YouthLongTermCollectiveInvestmentDeductionCalculator youthLongTermCollectiveInvestmentDeductionCalculator;
    private final IncomeTaxRateTableCalculator incomeTaxRateTableCalculator;
    private final EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator;
    private final StandardTaxCreditCalculator standardTaxCreditCalculator;

    public DefaultTaxCalculationService(
        EarnedIncomeDeductionCalculator earnedIncomeDeductionCalculator,
        PersonalDeductionCalculator personalDeductionCalculator,
        PensionInsurancePremiumCalculator pensionInsurancePremiumCalculator,
        SocialInsurancePremiumCalculator socialInsurancePremiumCalculator,
        CreditCardDeductionCalculator creditCardDeductionCalculator,
        DonationTaxCreditCalculator donationTaxCreditCalculator,
        ChildTaxCreditCalculator childTaxCreditCalculator,
        PensionAccountTaxCreditCalculator pensionAccountTaxCreditCalculator,
        MonthlyRentTaxCreditCalculator monthlyRentTaxCreditCalculator,
        HousingLoanDeductionCalculator housingLoanDeductionCalculator,
        LongTermMortgageDeductionCalculator longTermMortgageDeductionCalculator,
        HousingSavingsDeductionCalculator housingSavingsDeductionCalculator,
        LongTermCollectiveInvestmentDeductionCalculator longTermCollectiveInvestmentDeductionCalculator,
        YouthLongTermCollectiveInvestmentDeductionCalculator youthLongTermCollectiveInvestmentDeductionCalculator,
        IncomeTaxRateTableCalculator incomeTaxRateTableCalculator,
        EarnedIncomeTaxCreditCalculator earnedIncomeTaxCreditCalculator,
        StandardTaxCreditCalculator standardTaxCreditCalculator
    ) {
        this.earnedIncomeDeductionCalculator = earnedIncomeDeductionCalculator;
        this.personalDeductionCalculator = personalDeductionCalculator;
        this.pensionInsurancePremiumCalculator = pensionInsurancePremiumCalculator;
        this.socialInsurancePremiumCalculator = socialInsurancePremiumCalculator;
        this.creditCardDeductionCalculator = creditCardDeductionCalculator;
        this.donationTaxCreditCalculator = donationTaxCreditCalculator;
        this.childTaxCreditCalculator = childTaxCreditCalculator;
        this.pensionAccountTaxCreditCalculator = pensionAccountTaxCreditCalculator;
        this.monthlyRentTaxCreditCalculator = monthlyRentTaxCreditCalculator;
        this.housingLoanDeductionCalculator = housingLoanDeductionCalculator;
        this.longTermMortgageDeductionCalculator = longTermMortgageDeductionCalculator;
        this.housingSavingsDeductionCalculator = housingSavingsDeductionCalculator;
        this.longTermCollectiveInvestmentDeductionCalculator = longTermCollectiveInvestmentDeductionCalculator;
        this.youthLongTermCollectiveInvestmentDeductionCalculator = youthLongTermCollectiveInvestmentDeductionCalculator;
        this.incomeTaxRateTableCalculator = incomeTaxRateTableCalculator;
        this.earnedIncomeTaxCreditCalculator = earnedIncomeTaxCreditCalculator;
        this.standardTaxCreditCalculator = standardTaxCreditCalculator;
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
        HousingLoanRuleSnapshot housingLoanRuleSnapshot =
            housingLoanDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        HousingLoanCalculation housingLoanCalculation = housingLoanDeductionCalculator.calculate(
            command.housingLoanBankRepaymentAmount(),
            command.housingLoanIndividualRepaymentAmount(),
            housingLoanRuleSnapshot
        );
        long housingLoanDeductionAmount = housingLoanCalculation.housingLoanDeductionAmount();
        LongTermMortgageRuleSnapshot longTermMortgageRuleSnapshot =
            longTermMortgageDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        LongTermMortgageCalculation longTermMortgageCalculation = longTermMortgageDeductionCalculator.calculate(
            command.longTermMortgageInterestAmount(),
            command.longTermMortgageRepaymentType(),
            longTermMortgageRuleSnapshot
        );
        long longTermMortgageDeductionAmount = longTermMortgageCalculation.longTermMortgageDeductionAmount();
        HousingSavingsRuleSnapshot housingSavingsRuleSnapshot =
            housingSavingsDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        HousingSavingsCalculation housingSavingsCalculation = housingSavingsDeductionCalculator.calculate(
            command.housingSavingsContributionAmount(),
            housingSavingsRuleSnapshot
        );
        long housingSavingsDeductionAmount = housingSavingsCalculation.housingSavingsDeductionAmount();
        LongTermCollectiveInvestmentRuleSnapshot longTermCollectiveInvestmentRuleSnapshot =
            longTermCollectiveInvestmentDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        LongTermCollectiveInvestmentCalculation longTermCollectiveInvestmentCalculation =
            longTermCollectiveInvestmentDeductionCalculator.calculate(
                command.longTermCollectiveInvestmentContributionAmount(),
                longTermCollectiveInvestmentRuleSnapshot
            );
        long longTermCollectiveInvestmentDeductionAmount =
            longTermCollectiveInvestmentCalculation.longTermCollectiveInvestmentDeductionAmount();
        YouthLongTermCollectiveInvestmentRuleSnapshot youthLongTermCollectiveInvestmentRuleSnapshot =
            youthLongTermCollectiveInvestmentDeductionCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        YouthLongTermCollectiveInvestmentCalculation youthLongTermCollectiveInvestmentCalculation =
            youthLongTermCollectiveInvestmentDeductionCalculator.calculate(
                command.youthLongTermCollectiveInvestmentContributionAmount(),
                youthLongTermCollectiveInvestmentRuleSnapshot
            );
        long youthLongTermCollectiveInvestmentDeductionAmount =
            youthLongTermCollectiveInvestmentCalculation.youthLongTermCollectiveInvestmentDeductionAmount();
        long totalDeductionAmount = itemDeductionAmount + personalDeductionAmount + pensionInsurancePremiumDeductionAmount + socialInsurancePremiumDeductionAmount + creditCardDeductionAmount + housingLoanDeductionAmount + longTermMortgageDeductionAmount + housingSavingsDeductionAmount + longTermCollectiveInvestmentDeductionAmount + youthLongTermCollectiveInvestmentDeductionAmount;
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
        DonationRuleSnapshot donationRuleSnapshot =
            donationTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        DonationCalculation donationCalculation = donationTaxCreditCalculator.calculate(
            command.donationPoliticalAmount(),
            command.donationLegalAmount(),
            command.donationEmployeeStockAmount(),
            command.donationDesignatedAmount(),
            command.donationDesignatedReligiousAmount(),
            earnedIncomeAmount,
            donationRuleSnapshot
        );
        long donationTaxCreditAmount = donationCalculation.totalCreditAmount();
        ChildTaxCreditRuleSnapshot childTaxCreditRuleSnapshot =
            childTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        ChildTaxCreditCalculation childTaxCreditCalculation = childTaxCreditCalculator.calculate(
            command.taxYear(),
            command.dependents(),
            command.basicInfoAttributes(),
            childTaxCreditRuleSnapshot
        );
        long childTaxCreditAmount = childTaxCreditCalculation.totalCreditAmount();
        PensionAccountRuleSnapshot pensionAccountRuleSnapshot =
            pensionAccountTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        PensionAccountCalculation pensionAccountCalculation = pensionAccountTaxCreditCalculator.calculate(
            command.pensionSavingsAmount(),
            command.irpAmount(),
            command.isaMaturityTransferAmount(),
            command.totalGrossSalaryAmount(),
            pensionAccountRuleSnapshot
        );
        long pensionAccountTaxCreditAmount = pensionAccountCalculation.totalCreditAmount();
        MonthlyRentRuleSnapshot monthlyRentRuleSnapshot =
            monthlyRentTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        MonthlyRentCalculation monthlyRentCalculation = monthlyRentTaxCreditCalculator.calculate(
            command.annualRentAmount(),
            command.totalGrossSalaryAmount(),
            monthlyRentRuleSnapshot
        );
        long monthlyRentTaxCreditAmount = monthlyRentCalculation.totalCreditAmount();
        long specialTaxCreditFromDecisions = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .filter(d -> d.deductionType() == DeductionType.INSURANCE
                || d.deductionType() == DeductionType.MEDICAL_EXPENSE
                || d.deductionType() == DeductionType.EDUCATION_EXPENSE)
            .mapToLong(DeductionDecision::taxCreditContribution)
            .sum();
        long nonSpecialOtherTaxCreditAmount = command.deductionDecisions().stream()
            .filter(DeductionDecision::eligible)
            .filter(d -> d.deductionType() != DeductionType.INSURANCE
                && d.deductionType() != DeductionType.MEDICAL_EXPENSE
                && d.deductionType() != DeductionType.EDUCATION_EXPENSE)
            .mapToLong(DeductionDecision::taxCreditContribution)
            .sum();
        long specialTaxCreditGrandTotal = specialTaxCreditFromDecisions + donationTaxCreditAmount;
        boolean hasEarnedIncome = command.totalGrossSalaryAmount() > 0L;
        StandardTaxCreditRuleSnapshot standardTaxCreditRuleSnapshot =
            standardTaxCreditCalculator.resolveRuleSnapshot(command.ruleSetSnapshot());
        StandardTaxCreditCalculation standardTaxCreditCalculation = standardTaxCreditCalculator.calculate(
            specialTaxCreditGrandTotal,
            hasEarnedIncome,
            standardTaxCreditRuleSnapshot
        );
        long chosenSpecialOrStandardCreditAmount = standardTaxCreditCalculation.appliedAmount();
        long taxCreditAmount = earnedIncomeTaxCreditAmount + chosenSpecialOrStandardCreditAmount + childTaxCreditAmount + pensionAccountTaxCreditAmount + monthlyRentTaxCreditAmount + nonSpecialOtherTaxCreditAmount;
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
        trace.add("ruleCode " + HOUSING_LOAN_LIMIT_CODE + " applied");
        trace.add("housingLoanBankRepaymentAmount = " + housingLoanCalculation.housingLoanBankRepaymentAmount());
        trace.add("housingLoanIndividualRepaymentAmount = " + housingLoanCalculation.housingLoanIndividualRepaymentAmount());
        trace.add("housingLoanTotalRepaymentAmount = " + housingLoanCalculation.totalRepaymentAmount());
        trace.add("ruleCode " + HOUSING_LOAN_RATE_CODE + " applied");
        trace.add("housingLoanDeductionBeforeLimitAmount = " + housingLoanCalculation.deductionBeforeLimitAmount());
        trace.add("housingLoanDeductionAmount = " + housingLoanDeductionAmount);
        trace.add("ruleCode " + LONG_TERM_MORTGAGE_LIMIT_CODE + " applied");
        trace.add("longTermMortgageInterestAmount = " + longTermMortgageCalculation.longTermMortgageInterestAmount());
        trace.add("longTermMortgageRepaymentType = " + longTermMortgageCalculation.longTermMortgageRepaymentType());
        trace.add("longTermMortgageLimitAmount = " + longTermMortgageCalculation.longTermMortgageLimitAmount());
        trace.add("longTermMortgageDeductionAmount = " + longTermMortgageDeductionAmount);
        trace.add("ruleCode " + HOUSING_SAVINGS_LIMIT_CODE + " applied");
        trace.add("housingSavingsContributionAmount = " + housingSavingsCalculation.housingSavingsContributionAmount());
        trace.add("ruleCode " + HOUSING_SAVINGS_RATE_CODE + " applied");
        trace.add("housingSavingsDeductionBeforeLimitAmount = " + housingSavingsCalculation.deductionBeforeLimitAmount());
        trace.add("housingSavingsDeductionAmount = " + housingSavingsDeductionAmount);
        trace.add("ruleCode " + LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_CODE + " applied");
        trace.add("longTermCollectiveInvestmentContributionAmount = " + longTermCollectiveInvestmentCalculation.longTermCollectiveInvestmentContributionAmount());
        trace.add("ruleCode " + LONG_TERM_COLLECTIVE_INVESTMENT_RATE_CODE + " applied");
        trace.add("longTermCollectiveInvestmentDeductionBeforeLimitAmount = " + longTermCollectiveInvestmentCalculation.deductionBeforeLimitAmount());
        trace.add("longTermCollectiveInvestmentDeductionAmount = " + longTermCollectiveInvestmentDeductionAmount);
        trace.add("ruleCode " + YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_LIMIT_CODE + " applied");
        trace.add("youthLongTermCollectiveInvestmentContributionAmount = " + youthLongTermCollectiveInvestmentCalculation.youthLongTermCollectiveInvestmentContributionAmount());
        trace.add("ruleCode " + YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT_RATE_CODE + " applied");
        trace.add("youthLongTermCollectiveInvestmentDeductionBeforeLimitAmount = " + youthLongTermCollectiveInvestmentCalculation.deductionBeforeLimitAmount());
        trace.add("youthLongTermCollectiveInvestmentDeductionAmount = " + youthLongTermCollectiveInvestmentDeductionAmount);
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
        trace.add("ruleCode " + donationRuleSnapshot.politicalBracketRule().ruleCode() + " applied");
        trace.add("politicalDonationAmount = " + donationCalculation.effectivePoliticalAmount());
        trace.add("politicalDonationCreditAmount = " + donationCalculation.politicalCreditAmount());
        trace.add("ruleCode " + donationRuleSnapshot.legalLimitRateRule().ruleCode() + " applied");
        trace.add("legalDonationAmount = " + donationCalculation.effectiveLegalAmount());
        trace.add("legalDonationCreditAmount = " + donationCalculation.legalCreditAmount());
        trace.add("ruleCode " + donationRuleSnapshot.employeeStockRule().ruleCode() + " applied");
        trace.add("employeeStockDonationAmount = " + donationCalculation.effectiveEmployeeStockAmount());
        trace.add("employeeStockDonationCreditAmount = " + donationCalculation.employeeStockCreditAmount());
        trace.add("ruleCode " + donationRuleSnapshot.designatedRule().ruleCode() + " applied");
        trace.add("designatedDonationAmount = " + donationCalculation.effectiveDesignatedAmount());
        trace.add("designatedDonationCreditAmount = " + donationCalculation.designatedCreditAmount());
        trace.add("ruleCode " + donationRuleSnapshot.designatedReligiousRule().ruleCode() + " applied");
        trace.add("designatedReligiousDonationAmount = " + donationCalculation.effectiveDesignatedReligiousAmount());
        trace.add("designatedReligiousDonationCreditAmount = " + donationCalculation.designatedReligiousCreditAmount());
        trace.add("ruleCode " + donationRuleSnapshot.aggregateFormulaRule().ruleCode() + " applied");
        trace.add("ruleCode " + donationRuleSnapshot.traceRule().ruleCode() + " applied");
        trace.add("donationTaxCreditAmount = " + donationTaxCreditAmount);
        trace.add("ruleCode " + CHILD_TAX_CREDIT_AMOUNT_CODE + " applied");
        trace.add("eligibleChildCount = " + childTaxCreditCalculation.eligibleChildCount());
        trace.add("basicChildCreditAmount = " + childTaxCreditCalculation.basicChildCreditAmount());
        trace.add("ruleCode " + CHILD_TAX_CREDIT_BIRTH_ADOPTION_CODE + " applied");
        trace.add("birthAdoptionChildCount = " + childTaxCreditCalculation.birthAdoptionChildCount());
        trace.add("birthAdoptionCreditAmount = " + childTaxCreditCalculation.birthAdoptionCreditAmount());
        trace.add("childTaxCreditAmount = " + childTaxCreditAmount);
        trace.add("ruleCode " + PENSION_ACCOUNT_LIMIT_CODE + " applied");
        trace.add("pensionSavingsAmount = " + pensionAccountCalculation.pensionSavingsAmount());
        trace.add("irpAmount = " + pensionAccountCalculation.irpAmount());
        trace.add("applicablePensionSavingsAmount = " + pensionAccountCalculation.applicablePensionSavingsAmount());
        trace.add("applicableCombinedAmount = " + pensionAccountCalculation.applicableCombinedAmount());
        trace.add("ruleCode " + PENSION_ACCOUNT_RATE_CODE + " applied");
        trace.add("isaMaturityTransferAmount = " + pensionAccountCalculation.isaMaturityTransferAmount());
        trace.add("isaAdditionalAmount = " + pensionAccountCalculation.isaAdditionalAmount());
        trace.add("totalApplicableAmount = " + pensionAccountCalculation.totalApplicableAmount());
        trace.add("pensionAccountCreditRate = " + pensionAccountCalculation.creditRate());
        trace.add("pensionAccountTaxCreditAmount = " + pensionAccountTaxCreditAmount);
        trace.add("ruleCode " + MONTHLY_RENT_LIMIT_CODE + " applied");
        trace.add("annualRentAmount = " + monthlyRentCalculation.annualRentAmount());
        trace.add("eligibleRentAmount = " + monthlyRentCalculation.eligibleRentAmount());
        trace.add("ruleCode " + MONTHLY_RENT_RATE_CODE + " applied");
        trace.add("monthlyRentCreditRate = " + monthlyRentCalculation.creditRate());
        trace.add("monthlyRentTaxCreditAmount = " + monthlyRentTaxCreditAmount);
        trace.add("ruleCode " + StandardTaxCreditCalculator.FLAT_AMOUNT_RULE_CODE + " applied");
        trace.add("ruleCode " + StandardTaxCreditCalculator.CHOICE_FORMULA_RULE_CODE + " applied");
        trace.add("specialTaxCreditTotal = " + standardTaxCreditCalculation.specialTaxCreditTotal());
        trace.add("standardFlatAmount = " + standardTaxCreditCalculation.standardFlatAmount());
        trace.add("standardTaxCreditChosenVariant = " + standardTaxCreditCalculation.chosenVariant());
        trace.add("standardTaxCreditAppliedAmount = " + standardTaxCreditCalculation.appliedAmount());
        trace.add("nonSpecialOtherTaxCreditAmount = " + nonSpecialOtherTaxCreditAmount);
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
            housingLoanDeductionAmount,
            longTermMortgageDeductionAmount,
            housingSavingsDeductionAmount,
            longTermCollectiveInvestmentDeductionAmount,
            youthLongTermCollectiveInvestmentDeductionAmount,
            totalDeductionAmount,
            taxableIncomeAmount,
            calculatedTaxAmount,
            earnedIncomeTaxCreditAmount,
            donationTaxCreditAmount,
            childTaxCreditAmount,
            pensionAccountTaxCreditAmount,
            monthlyRentTaxCreditAmount,
            nonSpecialOtherTaxCreditAmount,
            taxCreditAmount,
            finalTaxAmount,
            command.withholdingTax(),
            expectedRefundAmount,
            trace
        );
    }

}
