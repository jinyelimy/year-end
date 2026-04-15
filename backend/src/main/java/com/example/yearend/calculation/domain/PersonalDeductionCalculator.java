package com.example.yearend.calculation.domain;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.RelationType;
import com.example.yearend.taxsession.domain.ResidentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

@Component
public class PersonalDeductionCalculator {

    public static final String BASIC_AMOUNT_RULE_CODE = "PERSONAL_BASIC_DEDUCTION_AMOUNT_2025";
    public static final String BASIC_ELIGIBILITY_RULE_CODE = "PERSONAL_BASIC_ELIGIBILITY_2025";
    public static final String SENIOR_RULE_CODE = "PERSONAL_ADDITIONAL_SENIOR_2025";
    public static final String DISABLED_RULE_CODE = "PERSONAL_ADDITIONAL_DISABLED_2025";
    public static final String WOMAN_RULE_CODE = "PERSONAL_ADDITIONAL_WOMAN_2025";
    public static final String SINGLE_PARENT_RULE_CODE = "PERSONAL_ADDITIONAL_SINGLE_PARENT_2025";
    public static final String AGGREGATION_RULE_CODE = "PERSONAL_DEDUCTION_AGGREGATION_FORMULA_2025";

    private final ObjectMapper objectMapper;

    public PersonalDeductionCalculator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PersonalDeductionRuleSnapshot resolveRuleSnapshot(RuleSetSnapshot ruleSetSnapshot) {
        DeductionRule basicAmountRule = findRule(ruleSetSnapshot, BASIC_AMOUNT_RULE_CODE);
        DeductionRule basicEligibilityRule = findRule(ruleSetSnapshot, BASIC_ELIGIBILITY_RULE_CODE);
        DeductionRule seniorRule = findRule(ruleSetSnapshot, SENIOR_RULE_CODE);
        DeductionRule disabledRule = findRule(ruleSetSnapshot, DISABLED_RULE_CODE);
        DeductionRule womanRule = findRule(ruleSetSnapshot, WOMAN_RULE_CODE);
        DeductionRule singleParentRule = findRule(ruleSetSnapshot, SINGLE_PARENT_RULE_CODE);
        DeductionRule aggregationRule = findRule(ruleSetSnapshot, AGGREGATION_RULE_CODE);

        JsonNode basicAmountParameters = readParameters(basicAmountRule);
        JsonNode basicEligibilityParameters = readParameters(basicEligibilityRule);
        JsonNode seniorParameters = readParameters(seniorRule);
        JsonNode disabledParameters = readParameters(disabledRule);
        JsonNode womanParameters = readParameters(womanRule);
        JsonNode singleParentParameters = readParameters(singleParentRule);
        JsonNode aggregationParameters = readParameters(aggregationRule);

        return new PersonalDeductionRuleSnapshot(
            RuleReference.from(basicAmountRule),
            requiredLong(basicAmountParameters, "amountPerPerson", BASIC_AMOUNT_RULE_CODE),
            RuleReference.from(basicEligibilityRule),
            requiredLong(basicEligibilityParameters, "incomeLimitAmount", BASIC_ELIGIBILITY_RULE_CODE),
            requiredLong(basicEligibilityParameters, "parentMinAge", BASIC_ELIGIBILITY_RULE_CODE),
            requiredLong(basicEligibilityParameters, "childMaxAge", BASIC_ELIGIBILITY_RULE_CODE),
            optionalBoolean(basicEligibilityParameters, "disabledDependentsIgnoreAgeLimit", true),
            RuleReference.from(seniorRule),
            requiredLong(seniorParameters, "amountPerPerson", SENIOR_RULE_CODE),
            requiredLong(seniorParameters, "minAge", SENIOR_RULE_CODE),
            RuleReference.from(disabledRule),
            requiredLong(disabledParameters, "amountPerPerson", DISABLED_RULE_CODE),
            RuleReference.from(womanRule),
            requiredLong(womanParameters, "amount", WOMAN_RULE_CODE),
            requiredLong(womanParameters, "maxComprehensiveIncomeAmount", WOMAN_RULE_CODE),
            RuleReference.from(singleParentRule),
            requiredLong(singleParentParameters, "amount", SINGLE_PARENT_RULE_CODE),
            RuleReference.from(aggregationRule),
            optionalBoolean(aggregationParameters, "singleParentOverridesWoman", true)
        );
    }

    public PersonalDeductionCalculation calculate(
        int taxYear,
        long comprehensiveIncomeAmount,
        List<Dependent> dependents,
        Map<String, Object> basicInfoAttributes,
        PersonalDeductionRuleSnapshot ruleSnapshot
    ) {
        List<EligiblePerson> eligiblePeople = eligiblePeople(taxYear, dependents, ruleSnapshot);
        long basicDeductionAmount = eligiblePeople.size() * ruleSnapshot.basicAmountPerPerson();

        long seniorCount = eligiblePeople.stream()
            .filter(person -> person.ageAtYearEnd() >= ruleSnapshot.seniorMinAge())
            .count();
        long disabledCount = eligiblePeople.stream()
            .filter(EligiblePerson::disabled)
            .count();
        boolean hasEligibleChild = eligiblePeople.stream().anyMatch(EligiblePerson::child);

        long seniorDeductionAmount = seniorCount * ruleSnapshot.seniorAmountPerPerson();
        long disabledDeductionAmount = disabledCount * ruleSnapshot.disabledAmountPerPerson();
        boolean singleParentApplied = appliesSingleParentDeduction(hasEligibleChild, basicInfoAttributes);
        boolean womanApplied = !singleParentApplied && appliesWomanDeduction(
            comprehensiveIncomeAmount,
            hasEligibleDependent(eligiblePeople),
            basicInfoAttributes,
            ruleSnapshot
        );
        long singleParentDeductionAmount = singleParentApplied ? ruleSnapshot.singleParentAmount() : 0L;
        long womanDeductionAmount = womanApplied ? ruleSnapshot.womanAmount() : 0L;
        long totalPersonalDeductionAmount = Math.max(
            0L,
            basicDeductionAmount
                + seniorDeductionAmount
                + disabledDeductionAmount
                + singleParentDeductionAmount
                + womanDeductionAmount
        );

        return new PersonalDeductionCalculation(
            eligiblePeople.size(),
            basicDeductionAmount,
            seniorCount,
            seniorDeductionAmount,
            disabledCount,
            disabledDeductionAmount,
            womanApplied,
            womanDeductionAmount,
            singleParentApplied,
            singleParentDeductionAmount,
            totalPersonalDeductionAmount,
            ruleSnapshot
        );
    }

    private List<EligiblePerson> eligiblePeople(
        int taxYear,
        List<Dependent> dependents,
        PersonalDeductionRuleSnapshot ruleSnapshot
    ) {
        List<EligiblePerson> eligibleDependents = dependents.stream()
            .filter(dependent -> dependent.getRelationType() != RelationType.SELF)
            .filter(Dependent::isBasicDeductionTarget)
            .filter(dependent -> isEligibleDependent(taxYear, dependent, ruleSnapshot))
            .map(dependent -> EligiblePerson.fromDependent(taxYear, dependent))
            .toList();

        return java.util.stream.Stream.concat(
            java.util.stream.Stream.of(EligiblePerson.filerPerson()),
            eligibleDependents.stream()
        ).toList();
    }

    private boolean isEligibleDependent(
        int taxYear,
        Dependent dependent,
        PersonalDeductionRuleSnapshot ruleSnapshot
    ) {
        if (dependent.getResidentType() == ResidentType.NON_RESIDENT) {
            return false;
        }
        if (dependent.getAnnualIncomeAmount() != null
            && dependent.getAnnualIncomeAmount() > ruleSnapshot.incomeLimitAmount()) {
            return false;
        }
        if (dependent.getRelationType() == RelationType.SPOUSE) {
            return true;
        }
        if (!dependent.isLivesTogether()) {
            return false;
        }

        long age = ageAtYearEnd(taxYear, dependent.getBirthDate());
        if (ruleSnapshot.disabledDependentsIgnoreAgeLimit() && dependent.isDisabled()) {
            return true;
        }
        if (dependent.getRelationType() == RelationType.PARENT) {
            return age >= ruleSnapshot.parentMinAge();
        }
        if (dependent.getRelationType() == RelationType.CHILD) {
            return age <= ruleSnapshot.childMaxAge();
        }
        return false;
    }

    private boolean appliesSingleParentDeduction(
        boolean hasEligibleChild,
        Map<String, Object> basicInfoAttributes
    ) {
        return booleanAttribute(basicInfoAttributes, "claimsSingleParentDeduction")
            && Boolean.FALSE.equals(optionalBooleanAttribute(basicInfoAttributes, "hasSpouse"))
            && hasEligibleChild;
    }

    private boolean appliesWomanDeduction(
        long comprehensiveIncomeAmount,
        boolean hasEligibleDependent,
        Map<String, Object> basicInfoAttributes,
        PersonalDeductionRuleSnapshot ruleSnapshot
    ) {
        if (!booleanAttribute(basicInfoAttributes, "claimsWomanDeduction")
            || !booleanAttribute(basicInfoAttributes, "filerIsFemale")
            || comprehensiveIncomeAmount > ruleSnapshot.womanMaxComprehensiveIncomeAmount()) {
            return false;
        }

        boolean hasSpouse = Boolean.TRUE.equals(optionalBooleanAttribute(basicInfoAttributes, "hasSpouse"));
        boolean householdHeadWithDependent = booleanAttribute(basicInfoAttributes, "isHouseholdHead")
            && hasEligibleDependent;
        return hasSpouse || householdHeadWithDependent;
    }

    private boolean hasEligibleDependent(List<EligiblePerson> eligiblePeople) {
        return eligiblePeople.stream().anyMatch(person -> !person.filer());
    }

    private DeductionRule findRule(RuleSetSnapshot ruleSetSnapshot, String ruleCode) {
        return ruleSetSnapshot.rulesFor(DeductionType.PERSONAL_DEDUCTION).stream()
            .filter(rule -> ruleCode.equals(rule.getRuleCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "RuleSnapshot is missing required personal deduction ruleCode: " + ruleCode
            ));
    }

    private JsonNode readParameters(DeductionRule rule) {
        try {
            return objectMapper.readTree(rule.getParameterJsonb());
        } catch (Exception exception) {
            throw new IllegalStateException(
                "Failed to parse parameterJsonb for ruleCode " + rule.getRuleCode() + ".",
                exception
            );
        }
    }

    private long requiredLong(JsonNode node, String fieldName, String ruleCode) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            throw new IllegalStateException(ruleCode + " is missing parameter: " + fieldName);
        }
        return value.isNumber() ? value.longValue() : Long.parseLong(value.asText());
    }

    private boolean optionalBoolean(JsonNode node, String fieldName, boolean defaultValue) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.isBoolean() ? value.booleanValue() : Boolean.parseBoolean(value.asText());
    }

    private boolean booleanAttribute(Map<String, Object> attributes, String name) {
        return Boolean.TRUE.equals(optionalBooleanAttribute(attributes, name));
    }

    private Boolean optionalBooleanAttribute(Map<String, Object> attributes, String name) {
        Object value = attributes.get(name);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String textValue && !textValue.isBlank()) {
            return Boolean.parseBoolean(textValue);
        }
        return null;
    }

    private static long ageAtYearEnd(int taxYear, LocalDate birthDate) {
        if (birthDate == null) {
            return 0L;
        }
        return Period.between(birthDate, LocalDate.of(taxYear, 12, 31)).getYears();
    }

    private record EligiblePerson(
        boolean filer,
        RelationType relationType,
        long ageAtYearEnd,
        boolean disabled
    ) {

        static EligiblePerson filerPerson() {
            return new EligiblePerson(true, RelationType.SELF, 0L, false);
        }

        static EligiblePerson fromDependent(int taxYear, Dependent dependent) {
            return new EligiblePerson(
                false,
                dependent.getRelationType(),
                PersonalDeductionCalculator.ageAtYearEnd(taxYear, dependent.getBirthDate()),
                dependent.isDisabled()
            );
        }

        boolean child() {
            return relationType == RelationType.CHILD;
        }
    }

    public record PersonalDeductionCalculation(
        long basicTargetCount,
        long basicDeductionAmount,
        long seniorTargetCount,
        long seniorDeductionAmount,
        long disabledTargetCount,
        long disabledDeductionAmount,
        boolean womanDeductionApplied,
        long womanDeductionAmount,
        boolean singleParentDeductionApplied,
        long singleParentDeductionAmount,
        long totalPersonalDeductionAmount,
        PersonalDeductionRuleSnapshot ruleSnapshot
    ) {
    }

    public record PersonalDeductionRuleSnapshot(
        RuleReference basicAmountRule,
        long basicAmountPerPerson,
        RuleReference basicEligibilityRule,
        long incomeLimitAmount,
        long parentMinAge,
        long childMaxAge,
        boolean disabledDependentsIgnoreAgeLimit,
        RuleReference seniorRule,
        long seniorAmountPerPerson,
        long seniorMinAge,
        RuleReference disabledRule,
        long disabledAmountPerPerson,
        RuleReference womanRule,
        long womanAmount,
        long womanMaxComprehensiveIncomeAmount,
        RuleReference singleParentRule,
        long singleParentAmount,
        RuleReference aggregationRule,
        boolean singleParentOverridesWoman
    ) {
    }

    public record RuleReference(
        String ruleCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {

        static RuleReference from(DeductionRule rule) {
            return new RuleReference(rule.getRuleCode(), rule.getEffectiveFrom(), rule.getEffectiveTo());
        }
    }
}
