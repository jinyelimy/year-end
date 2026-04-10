package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.deduction.application.RuleSnapshotHasher;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.HumanReviewStatus;
import com.example.yearend.deduction.domain.RuleCategory;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRuleSetServiceTest {

    @Mock
    private TaxRuleSetRepository taxRuleSetRepository;

    @Mock
    private DeductionRuleRepository deductionRuleRepository;

    private AdminRuleSetService adminRuleSetService;

    @BeforeEach
    void setUp() {
        adminRuleSetService = new AdminRuleSetService(
            taxRuleSetRepository,
            deductionRuleRepository,
            new RuleVersionNormalizer(),
            new RuleSnapshotHasher(new ObjectMapper()),
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("human approval keeps the rule set in READY_FOR_REVIEW until publish")
    void reviewApprovalKeepsReadyForReview() {
        TaxRuleSet candidate = readyForReviewRuleSet("2025.04");
        when(taxRuleSetRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));

        AdminDtos.AdminRuleSetResponse response = adminRuleSetService.reviewRuleSet(
            "reviewer@example.com",
            candidate.getId(),
            new AdminDtos.ReviewRuleSetRequest(true, "Looks good.")
        );

        assertThat(response.status()).isEqualTo(RuleSetStatus.READY_FOR_REVIEW);
        assertThat(response.humanReviewStatus()).isEqualTo(HumanReviewStatus.APPROVED);
        assertThat(candidate.getReviewedBy()).isEqualTo("reviewer@example.com");
        assertThat(candidate.getReviewedAt()).isNotNull();
        assertThat(candidate.getReviewNotesJsonb()).contains("APPROVED");
    }

    @Test
    @DisplayName("publish rejects READY_FOR_REVIEW sets that do not have approved human review")
    void publishRequiresApprovedHumanReview() {
        TaxRuleSet candidate = readyForReviewRuleSet("2025.04");
        when(taxRuleSetRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> adminRuleSetService.publishRuleSet("publisher@example.com", candidate.getId()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("approved human review");
    }

    @Test
    @DisplayName("publish retires the previous published set and activates the reviewed candidate")
    void publishTransitionsToPublished() {
        TaxRuleSet previousPublished = publishedRuleSet("2025.03");
        TaxRuleSet candidate = readyForReviewRuleSet("2025.04");
        candidate.setHumanReviewStatus(HumanReviewStatus.APPROVED);
        candidate.setReviewedBy("reviewer@example.com");
        candidate.setReviewedAt(java.time.OffsetDateTime.now());
        candidate.setGitCommitSha("abc123def456");

        DeductionRule oldRule = deductionRule(previousPublished, "OLD_RULE");
        DeductionRule newRule = deductionRule(candidate, "NEW_RULE");

        when(taxRuleSetRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(taxRuleSetRepository.findFirstByTaxYearAndStatusOrderByPublishedAtDesc(2025, RuleSetStatus.PUBLISHED))
            .thenReturn(Optional.of(previousPublished));
        when(deductionRuleRepository.findAllByTaxRuleSetId(candidate.getId())).thenReturn(List.of(newRule));
        when(deductionRuleRepository.findAllByTaxRuleSetId(previousPublished.getId())).thenReturn(List.of(oldRule));

        AdminDtos.AdminRuleSetResponse response = adminRuleSetService.publishRuleSet("publisher@example.com", candidate.getId());

        assertThat(response.status()).isEqualTo(RuleSetStatus.PUBLISHED);
        assertThat(candidate.getStatus()).isEqualTo(RuleSetStatus.PUBLISHED);
        assertThat(candidate.getPublishedBy()).isEqualTo("publisher@example.com");
        assertThat(candidate.getPublishedAt()).isNotNull();
        assertThat(candidate.getPreviousPublishedRuleSetId()).isEqualTo(previousPublished.getId());
        assertThat(candidate.getRuleSnapshotHash()).hasSize(64);

        assertThat(previousPublished.getStatus()).isEqualTo(RuleSetStatus.RETIRED);
        assertThat(oldRule.isActive()).isFalse();
        assertThat(newRule.isActive()).isTrue();
    }

    private TaxRuleSet readyForReviewRuleSet(String ruleVersion) {
        TaxRuleSet ruleSet = new TaxRuleSet();
        ruleSet.setId(UUID.randomUUID());
        ruleSet.setTaxYear(2025);
        ruleSet.setRuleVersion(ruleVersion);
        ruleSet.setStatus(RuleSetStatus.READY_FOR_REVIEW);
        ruleSet.setHumanReviewStatus(HumanReviewStatus.PENDING);
        return ruleSet;
    }

    private TaxRuleSet publishedRuleSet(String ruleVersion) {
        TaxRuleSet ruleSet = readyForReviewRuleSet(ruleVersion);
        ruleSet.setStatus(RuleSetStatus.PUBLISHED);
        ruleSet.setHumanReviewStatus(HumanReviewStatus.APPROVED);
        ruleSet.setPublishedAt(java.time.OffsetDateTime.now().minusDays(1));
        ruleSet.setPublishedBy("previous@example.com");
        ruleSet.setGitCommitSha("git-" + ruleVersion);
        ruleSet.setRuleSnapshotHash("hash-" + ruleVersion);
        return ruleSet;
    }

    private DeductionRule deductionRule(TaxRuleSet ruleSet, String ruleCode) {
        DeductionRule rule = new DeductionRule();
        rule.setTaxYear(ruleSet.getTaxYear());
        rule.setTaxRuleSet(ruleSet);
        rule.setDeductionType(DeductionType.MEDICAL_EXPENSE);
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode);
        rule.setRuleVersion(1);
        rule.setRuleCategory(RuleCategory.RATE);
        rule.setParameterJsonb("{\"value\":1}");
        rule.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        rule.setActive(ruleSet.getStatus() == RuleSetStatus.PUBLISHED);
        return rule;
    }
}
