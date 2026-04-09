package com.example.yearend.deduction.domain;

import java.util.List;
import java.util.UUID;

public record DeductionDecision(
    UUID deductionItemId,
    DeductionType deductionType,
    boolean eligible,
    long requestedAmount,
    long eligibleAmount,
    long appliedAmount,
    long taxCreditContribution,
    List<String> reasons
) {
    public static DeductionDecision rejected(UUID itemId, DeductionType type, long requestedAmount, List<String> reasons) {
        return new DeductionDecision(itemId, type, false, requestedAmount, 0L, 0L, 0L, reasons);
    }
}
