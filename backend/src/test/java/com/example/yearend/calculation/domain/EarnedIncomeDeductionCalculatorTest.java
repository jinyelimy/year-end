package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.EarnedIncomeDeductionCalculator.EarnedIncomeDeductionRuleSnapshot;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EarnedIncomeDeductionCalculatorTest {

    private final EarnedIncomeDeductionCalculator calculator = new EarnedIncomeDeductionCalculator(new ObjectMapper());

    @Test
    @DisplayName("calculates bracket boundaries from the resolved rule snapshot")
    void calculatesBracketBoundaries() {
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());

        assertThat(ruleSnapshot.bracketsEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(ruleSnapshot.maxLimitEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(calculator.calculate(0L, ruleSnapshot)).isZero();
        assertThat(calculator.calculate(5_000_000L, ruleSnapshot)).isEqualTo(3_500_000L);
        assertThat(calculator.calculate(15_000_000L, ruleSnapshot)).isEqualTo(7_500_000L);
        assertThat(calculator.calculate(45_000_000L, ruleSnapshot)).isEqualTo(12_000_000L);
        assertThat(calculator.calculate(100_000_000L, ruleSnapshot)).isEqualTo(14_750_000L);
    }

    @Test
    @DisplayName("applies the maximum limit from the resolved rule snapshot")
    void appliesMaximumLimit() {
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());

        assertThat(calculator.calculate(400_000_000L, ruleSnapshot)).isEqualTo(20_000_000L);
    }

    @Test
    @DisplayName("uses changed rule parameters without code changes")
    void usesChangedRuleParameters() {
        RuleSetSnapshot customSnapshot = new RuleSetSnapshot(
            "custom-rule-set",
            "2025.99",
            "custom-hash",
            true,
            List.of(
                deductionRule(
                    EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {"brackets":[{"fromInclusive":0,"toInclusive":null,"baseDeductionAmount":0,"excessBaseAmount":0,"excessRatePercent":10}]}
                        """,
                    LocalDate.of(2025, 4, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeDeductionCalculator.MAX_LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":100}",
                    LocalDate.of(2025, 4, 1),
                    null
                )
            )
        );
        EarnedIncomeDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(customSnapshot);

        assertThat(calculator.calculate(10_000L, ruleSnapshot)).isEqualTo(100L);
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain earned income deduction rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE);
    }

    static RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@2025.01",
            "2025.01",
            "official-hash",
            true,
            List.of(
                deductionRule(
                    EarnedIncomeDeductionCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {
                          "brackets": [
                            {"fromInclusive":0,"toInclusive":5000000,"baseDeductionAmount":0,"excessBaseAmount":0,"excessRatePercent":70},
                            {"fromInclusive":5000001,"toInclusive":15000000,"baseDeductionAmount":3500000,"excessBaseAmount":5000000,"excessRatePercent":40},
                            {"fromInclusive":15000001,"toInclusive":45000000,"baseDeductionAmount":7500000,"excessBaseAmount":15000000,"excessRatePercent":15},
                            {"fromInclusive":45000001,"toInclusive":100000000,"baseDeductionAmount":12000000,"excessBaseAmount":45000000,"excessRatePercent":5},
                            {"fromInclusive":100000001,"toInclusive":null,"baseDeductionAmount":14750000,"excessBaseAmount":100000000,"excessRatePercent":2}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeDeductionCalculator.MAX_LIMIT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"limitAmount\":20000000}",
                    LocalDate.of(2025, 1, 1),
                    null
                ),
                deductionRule(
                    DeductionType.INCOME_TAX,
                    IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE,
                    RuleCategory.BRACKET,
                    """
                        {
                          "calculationMethod": "TAX_BASE_TIMES_RATE_MINUS_QUICK_DEDUCTION",
                          "brackets": [
                            {"sequence":1,"fromExclusive":null,"upToInclusive":14000000,"rate":0.06,"quickDeductionAmount":0},
                            {"sequence":2,"fromExclusive":14000000,"upToInclusive":50000000,"rate":0.15,"quickDeductionAmount":1260000},
                            {"sequence":3,"fromExclusive":50000000,"upToInclusive":88000000,"rate":0.24,"quickDeductionAmount":5760000},
                            {"sequence":4,"fromExclusive":88000000,"upToInclusive":150000000,"rate":0.35,"quickDeductionAmount":15440000},
                            {"sequence":5,"fromExclusive":150000000,"upToInclusive":300000000,"rate":0.38,"quickDeductionAmount":19940000},
                            {"sequence":6,"fromExclusive":300000000,"upToInclusive":500000000,"rate":0.40,"quickDeductionAmount":25940000},
                            {"sequence":7,"fromExclusive":500000000,"upToInclusive":1000000000,"rate":0.42,"quickDeductionAmount":35940000},
                            {"sequence":8,"fromExclusive":1000000000,"upToInclusive":null,"rate":0.45,"quickDeductionAmount":65940000}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 12, 31)
                )
            )
        );
    }

    private static DeductionRule deductionRule(
        String ruleCode,
        RuleCategory ruleCategory,
        String parameterJsonb,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        return deductionRule(
            DeductionType.EARNED_INCOME_DEDUCTION,
            ruleCode,
            ruleCategory,
            parameterJsonb,
            effectiveFrom,
            effectiveTo
        );
    }

    private static DeductionRule deductionRule(
        DeductionType deductionType,
        String ruleCode,
        RuleCategory ruleCategory,
        String parameterJsonb,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(deductionType);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parameterJsonb);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setActive(true);
        return rule;
    }
}
