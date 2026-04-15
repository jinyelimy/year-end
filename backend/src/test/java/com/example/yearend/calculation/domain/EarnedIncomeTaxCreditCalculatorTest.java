package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditCalculation;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator.EarnedIncomeTaxCreditRuleSnapshot;
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

class EarnedIncomeTaxCreditCalculatorTest {

    private final EarnedIncomeTaxCreditCalculator calculator = new EarnedIncomeTaxCreditCalculator(new ObjectMapper());

    @Test
    @DisplayName("calculates the Article 59 base credit formula from the rule snapshot")
    void calculatesBaseFormula() {
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        EarnedIncomeTaxCreditCalculation underThreshold = calculator.calculate(30_000_000L, 1_300_000L, ruleSnapshot);
        EarnedIncomeTaxCreditCalculation overThreshold = calculator.calculate(30_000_000L, 2_000_000L, ruleSnapshot);

        assertThat(underThreshold.baseCreditAmount()).isEqualTo(715_000L);
        assertThat(overThreshold.baseCreditAmount()).isEqualTo(925_000L);
    }

    @Test
    @DisplayName("applies the gross-salary based credit limit from the rule snapshot")
    void appliesGrossSalaryLimit() {
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(calculator.calculate(33_000_000L, 2_000_000L, ruleSnapshot).salaryBasedLimitAmount())
            .isEqualTo(740_000L);
        assertThat(calculator.calculate(50_000_000L, 2_000_000L, ruleSnapshot).salaryBasedLimitAmount())
            .isEqualTo(660_000L);
        assertThat(calculator.calculate(120_000_000L, 2_000_000L, ruleSnapshot).salaryBasedLimitAmount())
            .isEqualTo(500_000L);
        assertThat(calculator.calculate(130_000_000L, 2_000_000L, ruleSnapshot).salaryBasedLimitAmount())
            .isEqualTo(200_000L);
    }

    @Test
    @DisplayName("uses the smaller amount between base credit and gross-salary limit")
    void usesSmallerOfBaseCreditAndLimit() {
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(calculator.calculate(10_000_000L, 210_000L, ruleSnapshot).earnedIncomeTaxCreditAmount())
            .isEqualTo(115_500L);
        assertThat(calculator.calculate(50_000_000L, 2_000_000L, ruleSnapshot).earnedIncomeTaxCreditAmount())
            .isEqualTo(660_000L);
    }

    @Test
    @DisplayName("does not apply the credit when there is no employment gross salary")
    void doesNotApplyWithoutEmploymentSalary() {
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        EarnedIncomeTaxCreditCalculation calculation = calculator.calculate(0L, 3_240_000L, ruleSnapshot);

        assertThat(calculation.baseCreditAmount()).isZero();
        assertThat(calculation.salaryBasedLimitAmount()).isZero();
        assertThat(calculation.earnedIncomeTaxCreditAmount()).isZero();
    }

    @Test
    @DisplayName("keeps effective-date metadata for calculation trace")
    void keepsEffectiveDates() {
        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        assertThat(ruleSnapshot.baseFormulaRule().ruleCode())
            .isEqualTo(EarnedIncomeTaxCreditCalculator.BASE_FORMULA_RULE_CODE);
        assertThat(ruleSnapshot.limitRule().ruleCode())
            .isEqualTo(EarnedIncomeTaxCreditCalculator.LIMIT_BY_GROSS_SALARY_RULE_CODE);
        assertThat(ruleSnapshot.finalFormulaRule().ruleCode())
            .isEqualTo(EarnedIncomeTaxCreditCalculator.FINAL_FORMULA_RULE_CODE);
        assertThat(ruleSnapshot.baseFormulaEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(ruleSnapshot.limitEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(ruleSnapshot.finalFormulaEffectiveFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
    }

    @Test
    @DisplayName("uses changed credit rates and limits from the rule snapshot without code changes")
    void usesChangedRuleParameters() {
        RuleSetSnapshot customSnapshot = new RuleSetSnapshot(
            "custom-earned-credit",
            "2025.99",
            "custom-hash",
            true,
            List.of(
                deductionRule(
                    EarnedIncomeTaxCreditCalculator.BASE_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "brackets": [
                            {"sequence":1,"upToInclusive":null,"baseCreditAmount":0,"excessBaseAmount":0,"excessCreditRate":0.10}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 4, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeTaxCreditCalculator.LIMIT_BY_GROSS_SALARY_RULE_CODE,
                    RuleCategory.LIMIT,
                    """
                        {
                          "limitBrackets": [
                            {"sequence":1,"upToInclusive":null,"limitAmount":80}
                          ]
                        }
                        """,
                    LocalDate.of(2025, 4, 1),
                    null
                ),
                deductionRule(
                    EarnedIncomeTaxCreditCalculator.FINAL_FORMULA_RULE_CODE,
                    RuleCategory.FORMULA,
                    """
                        {
                          "formula": "min(baseCreditAmount, salaryBasedLimitAmount)",
                          "baseRuleCode": "EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025",
                          "limitRuleCode": "EARNED_INCOME_TAX_CREDIT_LIMIT_BY_GROSS_SALARY_2025"
                        }
                        """,
                    LocalDate.of(2025, 4, 1),
                    null
                )
            )
        );

        EarnedIncomeTaxCreditRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(customSnapshot);
        EarnedIncomeTaxCreditCalculation calculation = calculator.calculate(1_000L, 1_000L, ruleSnapshot);

        assertThat(calculation.baseCreditAmount()).isEqualTo(100L);
        assertThat(calculation.salaryBasedLimitAmount()).isEqualTo(80L);
        assertThat(calculation.earnedIncomeTaxCreditAmount()).isEqualTo(80L);
        assertThat(ruleSnapshot.baseFormulaEffectiveFrom()).isEqualTo(LocalDate.of(2025, 4, 1));
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain earned income tax credit rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(EarnedIncomeTaxCreditCalculator.BASE_FORMULA_RULE_CODE);
    }

    private static DeductionRule deductionRule(
        String ruleCode,
        RuleCategory ruleCategory,
        String parameterJsonb,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(DeductionType.INCOME_TAX);
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
