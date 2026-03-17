package com.example.yearend.deduction.domain;

public record EligibilityCheckResult(
    boolean passed,
    String reason
) {
    public static EligibilityCheckResult pass(String reason) {
        return new EligibilityCheckResult(true, reason);
    }

    public static EligibilityCheckResult fail(String reason) {
        return new EligibilityCheckResult(false, reason);
    }
}
