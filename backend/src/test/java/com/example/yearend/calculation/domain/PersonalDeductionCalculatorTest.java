package com.example.yearend.calculation.domain;

import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionCalculation;
import com.example.yearend.calculation.domain.PersonalDeductionCalculator.PersonalDeductionRuleSnapshot;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.RelationType;
import com.example.yearend.taxsession.domain.ResidentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersonalDeductionCalculatorTest {

    private final PersonalDeductionCalculator calculator = new PersonalDeductionCalculator(new ObjectMapper());

    @Test
    @DisplayName("always applies the filer basic personal deduction")
    void appliesSelfBasicDeduction() {
        PersonalDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PersonalDeductionCalculation calculation = calculator.calculate(2025, 4_500_000L, List.of(), Map.of(), ruleSnapshot);

        assertThat(calculation.basicTargetCount()).isEqualTo(1L);
        assertThat(calculation.basicDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(calculation.totalPersonalDeductionAmount()).isEqualTo(1_500_000L);
    }

    @Test
    @DisplayName("includes a spouse at the income threshold and excludes one above it")
    void evaluatesSpouseIncomeLimit() {
        PersonalDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PersonalDeductionCalculation calculation = calculator.calculate(
            2025,
            10_000_000L,
            List.of(
                dependent(RelationType.SPOUSE, LocalDate.of(1988, 1, 1), 1_000_000L, false, false, true),
                dependent(RelationType.SPOUSE, LocalDate.of(1988, 1, 1), 1_000_001L, false, false, true)
            ),
            Map.of(),
            ruleSnapshot
        );

        assertThat(calculation.basicTargetCount()).isEqualTo(2L);
        assertThat(calculation.basicDeductionAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("uses age boundaries and disabled age exception for child and parent dependents")
    void evaluatesAgeBoundaries() {
        PersonalDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PersonalDeductionCalculation calculation = calculator.calculate(
            2025,
            20_000_000L,
            List.of(
                dependent(RelationType.PARENT, LocalDate.of(1966, 1, 1), 0L, true, false, true),
                dependent(RelationType.PARENT, LocalDate.of(1965, 12, 31), 0L, true, false, true),
                dependent(RelationType.CHILD, LocalDate.of(2005, 12, 31), 0L, true, false, true),
                dependent(RelationType.CHILD, LocalDate.of(2004, 12, 31), 0L, true, false, true),
                dependent(RelationType.CHILD, LocalDate.of(2004, 12, 31), 0L, true, true, true)
            ),
            Map.of(),
            ruleSnapshot
        );

        assertThat(calculation.basicTargetCount()).isEqualTo(4L);
        assertThat(calculation.basicDeductionAmount()).isEqualTo(6_000_000L);
        assertThat(calculation.disabledTargetCount()).isEqualTo(1L);
        assertThat(calculation.totalPersonalDeductionAmount()).isEqualTo(8_000_000L);
    }

    @Test
    @DisplayName("stacks senior and disabled additional deductions for a basic target")
    void stacksSeniorAndDisabledDeductions() {
        PersonalDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PersonalDeductionCalculation calculation = calculator.calculate(
            2025,
            20_000_000L,
            List.of(dependent(RelationType.PARENT, LocalDate.of(1955, 1, 1), 0L, true, true, true)),
            Map.of(),
            ruleSnapshot
        );

        assertThat(calculation.basicDeductionAmount()).isEqualTo(3_000_000L);
        assertThat(calculation.seniorDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(calculation.disabledDeductionAmount()).isEqualTo(2_000_000L);
        assertThat(calculation.totalPersonalDeductionAmount()).isEqualTo(6_000_000L);
    }

    @Test
    @DisplayName("single-parent deduction suppresses woman deduction when both are claimed")
    void singleParentSuppressesWomanDeduction() {
        PersonalDeductionRuleSnapshot ruleSnapshot = calculator.resolveRuleSnapshot(
            EarnedIncomeDeductionCalculatorTest.officialRuleSetSnapshot()
        );

        PersonalDeductionCalculation calculation = calculator.calculate(
            2025,
            20_000_000L,
            List.of(dependent(RelationType.CHILD, LocalDate.of(2010, 1, 1), 0L, true, false, true)),
            Map.of(
                "claimsSingleParentDeduction", true,
                "claimsWomanDeduction", true,
                "filerIsFemale", true,
                "hasSpouse", false,
                "isHouseholdHead", true
            ),
            ruleSnapshot
        );

        assertThat(calculation.singleParentDeductionApplied()).isTrue();
        assertThat(calculation.singleParentDeductionAmount()).isEqualTo(1_000_000L);
        assertThat(calculation.womanDeductionApplied()).isFalse();
        assertThat(calculation.totalPersonalDeductionAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("fails fast when the snapshot does not contain personal deduction rules")
    void failsWhenRulesAreMissing() {
        RuleSetSnapshot emptySnapshot = new RuleSetSnapshot("empty", "2025.01", "hash", true, List.of());

        assertThatThrownBy(() -> calculator.resolveRuleSnapshot(emptySnapshot))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(PersonalDeductionCalculator.BASIC_AMOUNT_RULE_CODE);
    }

    private static Dependent dependent(
        RelationType relationType,
        LocalDate birthDate,
        long incomeAmount,
        boolean livesTogether,
        boolean disabled,
        boolean basicDeductionTarget
    ) {
        Dependent dependent = new Dependent();
        dependent.setRelationType(relationType);
        dependent.setBirthDate(birthDate);
        dependent.setAnnualIncomeAmount(incomeAmount);
        dependent.setResidentType(ResidentType.RESIDENT);
        dependent.setLivesTogether(livesTogether);
        dependent.setDisabled(disabled);
        dependent.setBasicDeductionTarget(basicDeductionTarget);
        return dependent;
    }
}
