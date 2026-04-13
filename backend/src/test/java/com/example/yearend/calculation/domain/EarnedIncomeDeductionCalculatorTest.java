package com.example.yearend.calculation.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EarnedIncomeDeductionCalculatorTest {

    private final EarnedIncomeDeductionCalculator calculator = new EarnedIncomeDeductionCalculator();

    @Test
    @DisplayName("근로소득공제 구간 경계값을 계산한다")
    void calculatesBracketBoundaries() {
        assertThat(calculator.calculate(0L)).isZero();
        assertThat(calculator.calculate(5_000_000L)).isEqualTo(3_500_000L);
        assertThat(calculator.calculate(15_000_000L)).isEqualTo(7_500_000L);
        assertThat(calculator.calculate(45_000_000L)).isEqualTo(12_000_000L);
        assertThat(calculator.calculate(100_000_000L)).isEqualTo(14_750_000L);
    }

    @Test
    @DisplayName("근로소득공제 최대 한도를 적용한다")
    void appliesMaximumLimit() {
        assertThat(calculator.calculate(400_000_000L)).isEqualTo(20_000_000L);
    }
}
