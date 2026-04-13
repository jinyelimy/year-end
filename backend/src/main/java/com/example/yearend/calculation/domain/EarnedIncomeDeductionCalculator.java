package com.example.yearend.calculation.domain;

public class EarnedIncomeDeductionCalculator {

    static final long FIRST_BRACKET_LIMIT = 5_000_000L;
    static final long SECOND_BRACKET_LIMIT = 15_000_000L;
    static final long THIRD_BRACKET_LIMIT = 45_000_000L;
    static final long FOURTH_BRACKET_LIMIT = 100_000_000L;
    static final long MAX_DEDUCTION_AMOUNT = 20_000_000L;

    public long calculate(long taxableSalaryAmount) {
        long baseAmount = Math.max(0L, taxableSalaryAmount);
        long deductionAmount;

        if (baseAmount <= FIRST_BRACKET_LIMIT) {
            deductionAmount = percentage(baseAmount, 70);
        } else if (baseAmount <= SECOND_BRACKET_LIMIT) {
            deductionAmount = 3_500_000L + percentage(baseAmount - FIRST_BRACKET_LIMIT, 40);
        } else if (baseAmount <= THIRD_BRACKET_LIMIT) {
            deductionAmount = 7_500_000L + percentage(baseAmount - SECOND_BRACKET_LIMIT, 15);
        } else if (baseAmount <= FOURTH_BRACKET_LIMIT) {
            deductionAmount = 12_000_000L + percentage(baseAmount - THIRD_BRACKET_LIMIT, 5);
        } else {
            deductionAmount = 14_750_000L + percentage(baseAmount - FOURTH_BRACKET_LIMIT, 2);
        }

        return Math.min(deductionAmount, MAX_DEDUCTION_AMOUNT);
    }

    private long percentage(long amount, int rate) {
        return Math.round(amount * (rate / 100.0));
    }
}
