package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.RelationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChildTaxCreditCalculatorTest {

    private ChildTaxCreditCalculator calculator;
    private RuleSetSnapshot ruleSetSnapshot;

    @BeforeEach
    void setUp() {
        calculator = new ChildTaxCreditCalculator(new ObjectMapper());
        ruleSetSnapshot = officialRuleSetSnapshot();
    }

    @Test
    @DisplayName("자녀 없음: 세액공제 0")
    void noChildren_zeroCreditAmount() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(), Map.of(), snap);

        assertThat(result.eligibleChildCount()).isZero();
        assertThat(result.basicChildCreditAmount()).isZero();
        assertThat(result.totalCreditAmount()).isZero();
    }

    @Test
    @DisplayName("8세 미만 자녀는 제외: 세액공제 0")
    void childUnderMinAge_excluded() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);
        // 7살 — 2025년 말 기준 7세
        Dependent child = buildChild(LocalDate.of(2018, 6, 15), true);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(child), Map.of(), snap);

        assertThat(result.eligibleChildCount()).isZero();
        assertThat(result.basicChildCreditAmount()).isZero();
    }

    @Test
    @DisplayName("8세 이상 자녀 1명: 15만원")
    void oneEligibleChild_150000() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);
        Dependent child = buildChild(LocalDate.of(2016, 3, 1), true); // 2025년 말 9세

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(child), Map.of(), snap);

        assertThat(result.eligibleChildCount()).isEqualTo(1);
        assertThat(result.basicChildCreditAmount()).isEqualTo(150_000L);
    }

    @Test
    @DisplayName("8세 이상 자녀 2명: 30만원")
    void twoEligibleChildren_300000() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);
        Dependent c1 = buildChild(LocalDate.of(2014, 1, 1), true); // 11세
        Dependent c2 = buildChild(LocalDate.of(2016, 1, 1), true); // 9세

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(c1, c2), Map.of(), snap);

        assertThat(result.eligibleChildCount()).isEqualTo(2);
        assertThat(result.basicChildCreditAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("8세 이상 자녀 3명: 30만원 + 30만원 = 60만원")
    void threeEligibleChildren_600000() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);
        Dependent c1 = buildChild(LocalDate.of(2012, 1, 1), true);
        Dependent c2 = buildChild(LocalDate.of(2014, 1, 1), true);
        Dependent c3 = buildChild(LocalDate.of(2016, 1, 1), true);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(c1, c2, c3), Map.of(), snap);

        assertThat(result.eligibleChildCount()).isEqualTo(3);
        assertThat(result.basicChildCreditAmount()).isEqualTo(600_000L);
    }

    @Test
    @DisplayName("출생·입양 자녀 1명(첫째): 30만원")
    void birthAdoptionFirstChild_300000() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(), Map.of("birthAdoptionChildCount", 1), snap);

        assertThat(result.birthAdoptionChildCount()).isEqualTo(1);
        assertThat(result.birthAdoptionCreditAmount()).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("출생·입양 자녀 2명(첫째+둘째): 30만원+50만원=80만원")
    void birthAdoptionTwoChildren_800000() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(), Map.of("birthAdoptionChildCount", 2), snap);

        assertThat(result.birthAdoptionCreditAmount()).isEqualTo(800_000L);
    }

    @Test
    @DisplayName("8세 이상 자녀 2명 + 출생 첫째: 30만원+30만원=60만원 합산")
    void combinedBasicAndBirthAdoption() {
        ChildTaxCreditCalculator.ChildTaxCreditRuleSnapshot snap =
            calculator.resolveRuleSnapshot(ruleSetSnapshot);
        Dependent c1 = buildChild(LocalDate.of(2014, 1, 1), true);
        Dependent c2 = buildChild(LocalDate.of(2016, 1, 1), true);

        ChildTaxCreditCalculator.ChildTaxCreditCalculation result =
            calculator.calculate(2025, List.of(c1, c2), Map.of("birthAdoptionChildCount", 1), snap);

        // basicChildCredit(2명)=30만, birthAdoptionCredit(첫째)=30만 → total=60만
        assertThat(result.basicChildCreditAmount()).isEqualTo(300_000L);
        assertThat(result.birthAdoptionCreditAmount()).isEqualTo(300_000L);
        assertThat(result.totalCreditAmount()).isEqualTo(600_000L);
    }

    private Dependent buildChild(LocalDate birthDate, boolean basicDeductionTarget) {
        Dependent dep = new Dependent();
        dep.setId(UUID.randomUUID());
        dep.setRelationType(RelationType.CHILD);
        dep.setBirthDate(birthDate);
        dep.setBasicDeductionTarget(basicDeductionTarget);
        dep.setAnnualIncomeAmount(0L);
        return dep;
    }

    private RuleSetSnapshot officialRuleSetSnapshot() {
        return new RuleSetSnapshot(
            "2025@test",
            "2025.01",
            "test-hash",
            false,
            List.of(
                rule("CHILD_TAX_CREDIT", "CHILD_TAX_CREDIT_AMOUNT_2025",
                    "{\"minAge\":8,\"firstChildAmount\":150000,\"secondChildAmount\":300000,\"additionalChildAmount\":300000}"),
                rule("CHILD_TAX_CREDIT", "CHILD_TAX_CREDIT_BIRTH_ADOPTION_2025",
                    "{\"firstChildAmount\":300000,\"secondChildAmount\":500000,\"thirdOrMoreChildAmount\":700000}"),
                rule("CHILD_TAX_CREDIT", "CHILD_TAX_CREDIT_TRACE_2025",
                    "{\"traceFields\":[\"eligibleChildCount\",\"basicChildCreditAmount\",\"birthAdoptionChildCount\",\"birthAdoptionCreditAmount\",\"childTaxCreditAmount\"]}")
            )
        );
    }

    private DeductionRule rule(String deductionType, String ruleCode, String parametersJson) {
        DeductionRule rule = new DeductionRule();
        rule.setDeductionType(DeductionType.valueOf(deductionType));
        rule.setRuleCode(ruleCode);
        rule.setParameterJsonb(parametersJson);
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setEffectiveTo(LocalDate.of(2025, 12, 31));
        return rule;
    }
}
