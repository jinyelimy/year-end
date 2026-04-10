package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import com.example.yearend.deduction.infrastructure.DeductionRuleRepository;
import com.example.yearend.deduction.infrastructure.TaxRuleSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleSetResolverTest {

    @Mock
    private DeductionRuleRepository deductionRuleRepository;

    @Mock
    private TaxRuleSetRepository taxRuleSetRepository;

    private RuleSetResolver ruleSetResolver;

    @BeforeEach
    void setUp() {
        ruleSetResolver = new RuleSetResolver(
            taxRuleSetRepository,
            deductionRuleRepository,
            new RuleVersionNormalizer(),
            new RuleSnapshotHasher(new ObjectMapper())
        );
    }

    @Test
    @DisplayName("normalizes a legacy requested rule version and filters effective rules")
    void resolvesApplicableRulesFromDeductionRules() {
        DeductionRule applicableRule = deductionRule(
            DeductionType.MEDICAL_EXPENSE,
            "MEDICAL_THRESHOLD_RATE",
            LocalDate.of(2025, 1, 1),
            null
        );
        DeductionRule expiredRule = deductionRule(
            DeductionType.INSURANCE,
            "INSURANCE_LIMIT",
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2025, 3, 31)
        );
        TaxRuleSet publishedRuleSet = publishedRuleSet("2025.04");

        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersionAndStatusOrderByPublishedAtDesc(
            2025,
            "2025.04",
            RuleSetStatus.PUBLISHED
        )).thenReturn(java.util.Optional.of(publishedRuleSet));
        when(deductionRuleRepository.findAllByTaxRuleSetIdAndIsActiveTrueOrderByEffectiveFromAscRuleCodeAsc(publishedRuleSet.getId()))
            .thenReturn(List.of(applicableRule, expiredRule));

        RuleSetSnapshot snapshot = ruleSetResolver.resolve(2025, "rule-2025.4", LocalDate.of(2025, 4, 10));

        assertThat(snapshot.ruleVersion()).isEqualTo("2025.04");
        assertThat(snapshot.ruleSetId()).isEqualTo(publishedRuleSet.getId().toString());
        assertThat(snapshot.dbBacked()).isTrue();
        assertThat(snapshot.rules()).containsExactly(applicableRule);
        assertThat(snapshot.ruleSnapshotHash()).isEqualTo("published-hash-2025.04");
    }

    @Test
    @DisplayName("rejects runtime resolution when the requested version has not been published")
    void rejectsWhenRequestedVersionIsNotPublished() {
        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersionAndStatusOrderByPublishedAtDesc(
            2025,
            "2025.01",
            RuleSetStatus.PUBLISHED
        )).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> ruleSetResolver.resolve(2025, "2025.1", LocalDate.of(2025, 4, 10)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("No published rule set");
    }

    @Test
    @DisplayName("rejects rule versions whose tax year does not match the session tax year")
    void rejectsTaxYearMismatch() {
        assertThatThrownBy(() -> ruleSetResolver.resolve(2025, "2024.12", LocalDate.of(2025, 4, 10)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("tax year");
    }

    private DeductionRule deductionRule(
        DeductionType deductionType,
        String ruleCode,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
    ) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(2025);
        rule.setDeductionType(deductionType);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(RuleCategory.RATE);
        rule.setParameterJsonb("{\"value\":1}");
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setActive(true);
        return rule;
    }

    private TaxRuleSet publishedRuleSet(String ruleVersion) {
        TaxRuleSet ruleSet = new TaxRuleSet();
        ruleSet.setId(UUID.randomUUID());
        ruleSet.setTaxYear(2025);
        ruleSet.setRuleVersion(ruleVersion);
        ruleSet.setStatus(RuleSetStatus.PUBLISHED);
        ruleSet.setRuleSnapshotHash("published-hash-" + ruleVersion);
        return ruleSet;
    }
}
