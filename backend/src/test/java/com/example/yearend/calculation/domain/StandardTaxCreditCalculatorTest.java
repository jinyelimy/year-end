package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardTaxCreditCalculatorTest {

    private StandardTaxCreditCalculator calculator;
    private StandardTaxCreditCalculator.StandardTaxCreditRuleSnapshot ruleSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new StandardTaxCreditCalculator(new ObjectMapper());
        ruleSnapshot = calculator.resolveRuleSnapshot(officialRuleSetSnapshot());
    }

    @Test
    @DisplayName("특별세액공제 합계 < 13만원: 표준세액공제 13만원 적용")
    void specialBelowFlat_standardWins() {
        var result = calculator.calculate(50_000L, true, ruleSnapshot);

        assertThat(result.appliedAmount()).isEqualTo(130_000L);
        assertThat(result.chosenVariant()).isEqualTo("STANDARD");
        assertThat(result.specialTaxCreditTotal()).isEqualTo(50_000L);
        assertThat(result.standardFlatAmount()).isEqualTo(130_000L);
    }

    @Test
    @DisplayName("특별세액공제 합계 > 13만원: 특별세액공제 그대로 적용")
    void specialAboveFlat_specialWins() {
        var result = calculator.calculate(500_000L, true, ruleSnapshot);

        assertThat(result.appliedAmount()).isEqualTo(500_000L);
        assertThat(result.chosenVariant()).isEqualTo("SPECIAL");
    }

    @Test
    @DisplayName("특별세액공제 합계 == 13만원: tieBreakPreference 에 따라 표준세액공제 선택")
    void specialEqualsFlat_tieBreakStandard() {
        var result = calculator.calculate(130_000L, true, ruleSnapshot);

        assertThat(result.appliedAmount()).isEqualTo(130_000L);
        assertThat(result.chosenVariant()).isEqualTo("STANDARD");
    }

    @Test
    @DisplayName("근로소득 없음: 표준세액공제 미적용, 특별세액공제만 반환")
    void noEarnedIncome_specialOnly() {
        var result = calculator.calculate(50_000L, false, ruleSnapshot);

        assertThat(result.appliedAmount()).isEqualTo(50_000L);
        assertThat(result.chosenVariant()).isEqualTo("SPECIAL");
        assertThat(result.standardFlatAmount()).isZero();
    }

    @Test
    @DisplayName("스냅샷에 rule 이 없으면 예외")
    void missingRule_throws() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(StandardTaxCreditCalculator.FLAT_AMOUNT_RULE_CODE);
    }

    static RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@2025.01",
            "2025.01",
            "official-hash",
            true,
            List.of(
                deductionRule(
                    StandardTaxCreditCalculator.FLAT_AMOUNT_RULE_CODE,
                    RuleCategory.LIMIT,
                    "{\"amountPerPerson\":130000}"
                ),
                deductionRule(
                    StandardTaxCreditCalculator.WORKER_ELIGIBILITY_RULE_CODE,
                    RuleCategory.ELIGIBILITY,
                    "{\"requiresEarnedIncome\":true}"
                ),
                deductionRule(
                    StandardTaxCreditCalculator.CHOICE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    "{\"method\":\"max\",\"competingSubTypes\":[\"INSURANCE_PREMIUM\",\"MEDICAL_EXPENSE\",\"EDUCATION_EXPENSE\",\"DONATION\"],\"tieBreakPreference\":\"STANDARD\"}"
                )
            )
        );
    }

    private static DeductionRule deductionRule(String ruleCode, RuleCategory ruleCategory, String parameterJsonb) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(DeductionType.STANDARD_TAX_CREDIT);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(ruleCategory);
        rule.setParameterJsonb(parameterJsonb);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        rule.setActive(true);
        return rule;
    }
}
