package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTaxCalculationServiceTest {

    private DefaultTaxCalculationService buildService() {
        return new DefaultTaxCalculationService(
            new EarnedIncomeDeductionCalculator(new ObjectMapper()),
            new PersonalDeductionCalculator(new ObjectMapper()),
            new PensionInsurancePremiumCalculator(new ObjectMapper()),
            new SocialInsurancePremiumCalculator(new ObjectMapper()),
            new CreditCardDeductionCalculator(new ObjectMapper()),
            new DonationTaxCreditCalculator(new ObjectMapper()),
            new ChildTaxCreditCalculator(new ObjectMapper()),
            new PensionAccountTaxCreditCalculator(new ObjectMapper()),
            new MonthlyRentTaxCreditCalculator(new ObjectMapper()),
            new HousingLoanDeductionCalculator(new ObjectMapper()),
            new LongTermMortgageDeductionCalculator(new ObjectMapper()),
            new HousingSavingsDeductionCalculator(new ObjectMapper()),
            new LongTermCollectiveInvestmentDeductionCalculator(new ObjectMapper()),
            new IncomeTaxRateTableCalculator(new ObjectMapper()),
            new EarnedIncomeTaxCreditCalculator(new ObjectMapper()),
            new StandardTaxCreditCalculator(new ObjectMapper())
        );
    }

    @Test
    @DisplayName("적용 가능한 공제만 반영해 예상 환급액을 계산한다")
    void calculateRefund() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            10_000_000L,
            0L,
            10_000_000L,
            0L,
            1_000_000L,
            0L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(
                new DeductionDecision(UUID.randomUUID(), DeductionType.MEDICAL_EXPENSE, true, 1_000_000L, 1_000_000L, 1_000_000L, 0L, List.of("applied")),
                new DeductionDecision(UUID.randomUUID(), DeductionType.DONATION, false, 500_000L, 0L, 0L, 0L, List.of("rejected"))
            ),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        assertThat(outcome.earnedIncomeDeductionAmount()).isEqualTo(5_500_000L);
        assertThat(outcome.earnedIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.totalIncomeAmount()).isEqualTo(4_500_000L);
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.pensionInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.totalDeductionAmount()).isEqualTo(2_500_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(2_000_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(120_000L);
        assertThat(outcome.earnedIncomeTaxCreditAmount()).isEqualTo(66_000L);
        assertThat(outcome.otherTaxCreditAmount()).isZero();
        assertThat(outcome.taxCreditAmount()).isEqualTo(196_000L);
        assertThat(outcome.finalTaxAmount()).isZero();
        assertThat(outcome.expectedRefundAmount()).isEqualTo(1_000_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains("standardTaxCreditChosenVariant = STANDARD"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("standardTaxCreditAppliedAmount = 130000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("EARNED_INCOME_DEDUCTION_BRACKETS"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("earnedIncomeTaxCreditAmount = 66000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("PERSONAL_BASIC_DEDUCTION_AMOUNT_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("personalDeductionAmount = 1500000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains(PensionInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("pensionInsurancePremiumDeductionAmount = 0"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("INCOME_TAX_BASIC_BRACKETS_2025"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("effectiveFrom = 2025-01-01"));
    }

    @Test
    @DisplayName("applies the income tax rate table from the resolved rule snapshot")
    void appliesIncomeTaxRateTable() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            30_000_000L,
            5_000_000L,
            0L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.pensionInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(28_500_000L);
        assertThat(outcome.calculatedTaxAmount()).isEqualTo(3_015_000L);
        assertThat(outcome.earnedIncomeTaxCreditAmount()).isZero();
        assertThat(outcome.finalTaxAmount()).isEqualTo(3_015_000L);
        assertThat(outcome.expectedRefundAmount()).isEqualTo(1_985_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxAppliedBracket = 2"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxRate = 0.15"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("incomeTaxQuickDeductionAmount = 1260000"));
    }

    @Test
    @DisplayName("deducts the reported public pension contribution amount under the aggregate income cap")
    void deductsPensionInsurancePremium() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            30_000_000L,
            0L,
            1_350_000L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.pensionInsurancePremiumDeductionAmount()).isEqualTo(1_350_000L);
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.totalDeductionAmount()).isEqualTo(2_850_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(27_150_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains(PensionInsurancePremiumCalculator.ELIGIBILITY_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains(PensionInsurancePremiumCalculator.PAID_AMOUNT_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("pensionEligiblePaidAmount = 1350000"));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("pensionInsurancePremiumDeductionAmount = 1350000"));
    }

    @Test
    @DisplayName("caps pension insurance premium at zero when comprehensive income is already exhausted by personal deduction")
    void capsPensionWhenPersonalDeductionExhaustsIncome() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            1_000_000L,
            0L,
            500_000L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        assertThat(outcome.totalIncomeAmount()).isEqualTo(1_000_000L);
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.pensionInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.trace()).anyMatch(line -> line.contains("pensionAggregateCapRemainingAmount = 0"));
    }

    @Test
    @DisplayName("deducts social insurance premiums (health + employment) after personal and pension deductions")
    void deductsSocialInsurancePremium() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            30_000_000L,
            0L,
            0L,
            1_200_000L,   // healthInsurancePremiumAmount
            300_000L,     // employmentInsurancePremiumAmount
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // personalDeduction=1_500_000, pension=0, social=1_500_000
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.pensionInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.totalDeductionAmount()).isEqualTo(3_000_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(27_000_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains(SocialInsurancePremiumCalculator.HEALTH_ELIGIBILITY_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains(SocialInsurancePremiumCalculator.EMPLOYMENT_ELIGIBILITY_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("socialInsurancePremiumDeductionAmount = 1500000"));
    }

    @Test
    @DisplayName("deducts credit card usage above the minimum threshold (신용카드 총급여 25% 초과분 공제)")
    void deductsCreditCardAboveMinimumThreshold() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            30_000_000L,
            0L,
            30_000_000L,
            0L,
            0L,
            0L,
            0L,
            0L,
            12_000_000L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // threshold = 30M * 25% = 7.5M; creditAbove = 12M - 7.5M = 4.5M; deduction = 4.5M * 15% = 675,000
        assertThat(outcome.creditCardDeductionAmount()).isEqualTo(675_000L);
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.totalDeductionAmount()).isEqualTo(2_175_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(18_075_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains(CreditCardDeductionCalculator.MINIMUM_USAGE_THRESHOLD_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("creditCardDeductionAmount = 675000"));
    }

    @Test
    @DisplayName("applies sequential threshold allocation across credit and debit card subtypes")
    void appliesSequentialThresholdWithMixedSubtypes() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            30_000_000L,
            0L,
            30_000_000L,
            0L,
            0L,
            0L,
            0L,
            0L,
            8_000_000L, 5_000_000L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // threshold=7.5M; credit=8M → creditAbove=500,000; debit=5M → debitAbove=5M
        // deduction = 500,000*15% + 5M*30% = 75,000 + 1,500,000 = 1,575,000
        assertThat(outcome.creditCardDeductionAmount()).isEqualTo(1_575_000L);
        assertThat(outcome.totalDeductionAmount()).isEqualTo(3_075_000L);
        assertThat(outcome.taxableIncomeAmount()).isEqualTo(17_175_000L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains(CreditCardDeductionCalculator.RATE_CREDIT_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains(CreditCardDeductionCalculator.RATE_DEBIT_CASH_ZEROPAY_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("creditCardDeductionAmount = 1575000"));
    }

    @Test
    @DisplayName("caps social insurance premium when prior deductions exhaust comprehensive income")
    void capsSocialInsuranceWhenPriorDeductionsExhaustIncome() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            0L,
            0L,
            0L,
            1_000_000L,   // comprehensiveIncome=1_000_000
            0L,
            0L,
            800_000L,     // health
            200_000L,     // employment
            0L, 0L, 0L, 0L,
            0L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // personalDeduction=1_500_000 > income=1_000_000 → social capRemaining=0
        assertThat(outcome.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(outcome.socialInsurancePremiumDeductionAmount()).isZero();
        assertThat(outcome.trace()).anyMatch(line -> line.contains("socialInsuranceAggregateCapRemainingAmount = 0"));
    }

    @Test
    @DisplayName("calculates legal donation tax credit at 15% rate for amounts below threshold (기부금 세액공제)")
    void appliesLegalDonationTaxCredit() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            30_000_000L,
            0L,
            30_000_000L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            0L, 2_000_000L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // earnedIncome = 30M - 9,750,000 = 20,250,000
        // legal limit = 20,250,000 × 100% → 2M is within limit
        // credit = 2M × 15% = 300,000 (below 10M threshold)
        assertThat(outcome.donationTaxCreditAmount()).isEqualTo(300_000L);
        assertThat(outcome.taxCreditAmount()).isEqualTo(
            outcome.earnedIncomeTaxCreditAmount() + outcome.donationTaxCreditAmount() + outcome.otherTaxCreditAmount()
        );
        assertThat(outcome.trace()).anyMatch(line -> line.contains(DonationTaxCreditCalculator.LEGAL_LIMIT_RATE_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("donationTaxCreditAmount = 300000"));
    }

    @Test
    @DisplayName("calculates political donation tax credit using bracket rates (정치자금 기부금 세액공제)")
    void appliesPoliticalDonationTaxCredit() {
        TaxCalculationCommand command = new TaxCalculationCommand(
            2025,
            30_000_000L,
            0L,
            30_000_000L,
            0L,
            0L,
            0L,
            0L,
            0L,
            0L, 0L, 0L, 0L,
            200_000L, 0L, 0L, 0L, 0L,
            0L, 0L, 0L,
            0L,
            0L, 0L,
            0L, "NONE",
            0L,
            0L,
            List.of(),
            Map.of(),
            List.of(),
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        TaxCalculationOutcome outcome = buildService().calculate(command);

        // below 100,000: Math.round(100,000 × 0.9091) = 90,910
        // above 100,000: Math.round(100,000 × 0.15) = 15,000
        // total = 105,910
        assertThat(outcome.donationTaxCreditAmount()).isEqualTo(105_910L);
        assertThat(outcome.trace()).anyMatch(line -> line.contains(DonationTaxCreditCalculator.POLITICAL_BRACKET_RULE_CODE));
        assertThat(outcome.trace()).anyMatch(line -> line.contains("donationTaxCreditAmount = 105910"));
    }
}
