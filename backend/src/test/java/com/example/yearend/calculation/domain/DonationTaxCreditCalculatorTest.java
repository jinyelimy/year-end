package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.DonationTaxCreditCalculator.DonationCalculation;
import com.example.yearend.calculation.domain.DonationTaxCreditCalculator.DonationRuleSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DonationTaxCreditCalculatorTest {

    private final DonationTaxCreditCalculator calculator = new DonationTaxCreditCalculator(new ObjectMapper());

    @Test
    @DisplayName("resolves donation rule snapshot from the official rule set")
    void resolvesRuleSnapshot() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(snapshot.politicalFirstThreshold()).isEqualTo(100_000L);
        assertThat(snapshot.politicalFirstRate()).isEqualTo(0.9091);
        assertThat(snapshot.legalIncomeLimitRate()).isEqualTo(1.0);
        assertThat(snapshot.creditRateBelowThreshold()).isEqualTo(0.15);
        assertThat(snapshot.creditRateAboveThreshold()).isEqualTo(0.30);
        assertThat(snapshot.creditThresholdAmount()).isEqualTo(10_000_000L);
        assertThat(snapshot.designatedIncomeLimitRate()).isEqualTo(0.30);
        assertThat(snapshot.designatedReligiousIncomeLimitRate()).isEqualTo(0.10);
        assertThat(snapshot.employeeStockIncomeLimitRate()).isEqualTo(0.30);
    }

    @Test
    @DisplayName("returns zero credit when all donation amounts are zero")
    void returnsZeroWhenNoDonation() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        DonationCalculation result = calculator.calculate(0L, 0L, 0L, 0L, 0L, 20_000_000L, snapshot);

        assertThat(result.totalCreditAmount()).isZero();
        assertThat(result.politicalCreditAmount()).isZero();
        assertThat(result.legalCreditAmount()).isZero();
        assertThat(result.employeeStockCreditAmount()).isZero();
        assertThat(result.designatedCreditAmount()).isZero();
        assertThat(result.designatedReligiousCreditAmount()).isZero();
    }

    @Test
    @DisplayName("calculates legal donation credit at 15% for amounts below the 10M threshold")
    void calculatesLegalDonationCreditBelowThreshold() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // legal=2M < threshold=10M → credit = 2M × 15% = 300,000
        DonationCalculation result = calculator.calculate(0L, 2_000_000L, 0L, 0L, 0L, 20_000_000L, snapshot);

        assertThat(result.effectiveLegalAmount()).isEqualTo(2_000_000L);
        assertThat(result.legalCreditAmount()).isEqualTo(300_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("calculates legal donation credit with 30% rate applied to portion above the 10M threshold")
    void calculatesLegalDonationCreditAboveThreshold() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // legal=12M > threshold=10M → 10M×15% + 2M×30% = 1,500,000 + 600,000 = 2,100,000
        DonationCalculation result = calculator.calculate(0L, 12_000_000L, 0L, 0L, 0L, 20_000_000L, snapshot);

        assertThat(result.effectiveLegalAmount()).isEqualTo(12_000_000L);
        assertThat(result.legalCreditAmount()).isEqualTo(2_100_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(2_100_000L);
    }

    @Test
    @DisplayName("caps effective legal donation amount at the income limit rate (100% of earned income)")
    void capsLegalDonationAtIncomeLimitRate() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // earnedIncome=10M, legal=15M → capped at 10M; credit = 10M×15% = 1,500,000
        DonationCalculation result = calculator.calculate(0L, 15_000_000L, 0L, 0L, 0L, 10_000_000L, snapshot);

        assertThat(result.effectiveLegalAmount()).isEqualTo(10_000_000L);
        assertThat(result.legalCreditAmount()).isEqualTo(1_500_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("calculates political donation credit using two-tier bracket rates")
    void calculatesPoliticalDonationCredit() {
        DonationRuleSnapshot snapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        // political=200,000 → below 100,000: Math.round(100,000 × 0.9091) = 90,910
        //                      above 100,000: Math.round(100,000 × 0.15) = 15,000 → total = 105,910
        DonationCalculation result = calculator.calculate(200_000L, 0L, 0L, 0L, 0L, 20_000_000L, snapshot);

        assertThat(result.effectivePoliticalAmount()).isEqualTo(200_000L);
        assertThat(result.politicalCreditAmount()).isEqualTo(105_910L);
        assertThat(result.totalCreditAmount()).isEqualTo(105_910L);
    }
}
