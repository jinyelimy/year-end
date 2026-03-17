package com.example.yearend.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount) {

    public static Money of(long value) {
        return new Money(BigDecimal.valueOf(value));
    }

    public static Money zero() {
        return of(0);
    }

    public Money add(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money subtract(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    public Money multiply(BigDecimal rate) {
        return new Money(amount.multiply(rate).setScale(0, RoundingMode.DOWN));
    }

    public Money max(Money other) {
        return amount.compareTo(other.amount) >= 0 ? this : other;
    }

    public Money min(Money other) {
        return amount.compareTo(other.amount) <= 0 ? this : other;
    }
}
