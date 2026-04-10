package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.RuleSnapshotHasher;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.HumanReviewStatus;
import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import com.example.yearend.deduction.infrastructure.DeductionRuleRepository;
import com.example.yearend.deduction.infrastructure.TaxRuleSetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRuleSetService {

    private final TaxRuleSetRepository taxRuleSetRepository;
    private final DeductionRuleRepository deductionRuleRepository;
    private final RuleVersionNormalizer ruleVersionNormalizer;
    private final RuleSnapshotHasher ruleSnapshotHasher;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminDtos.AdminRuleSetResponse reviewRuleSet(
        String reviewerEmail,
        UUID ruleSetId,
        AdminDtos.ReviewRuleSetRequest request
    ) {
        TaxRuleSet ruleSet = getRuleSet(ruleSetId);
        if (ruleSet.getStatus() != RuleSetStatus.READY_FOR_REVIEW) {
            throw new BusinessException(ErrorCode.RULE_SET_REVIEW_NOT_ALLOWED);
        }

        ruleSet.setReviewedBy(reviewerEmail);
        ruleSet.setReviewedAt(OffsetDateTime.now());
        ruleSet.setReviewNotesJsonb(toReviewNotesJson(reviewerEmail, request));

        if (request.approved()) {
            ruleSet.setHumanReviewStatus(HumanReviewStatus.APPROVED);
        } else {
            ruleSet.setHumanReviewStatus(HumanReviewStatus.REJECTED);
            ruleSet.setStatus(RuleSetStatus.BLOCKED);
        }

        return toResponse(ruleSet);
    }

    @Transactional
    public AdminDtos.AdminRuleSetResponse publishRuleSet(String publisherEmail, UUID ruleSetId) {
        TaxRuleSet candidate = getRuleSet(ruleSetId);
        if (candidate.getStatus() != RuleSetStatus.READY_FOR_REVIEW) {
            throw new BusinessException(ErrorCode.RULE_SET_PUBLISH_NOT_ALLOWED);
        }
        if (candidate.getHumanReviewStatus() != HumanReviewStatus.APPROVED
            || candidate.getReviewedAt() == null
            || candidate.getReviewedBy() == null
            || candidate.getReviewedBy().isBlank()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_PUBLISH_NOT_ALLOWED,
                "A READY_FOR_REVIEW rule set can be published only after an approved human review."
            );
        }

        String canonicalRuleVersion = ruleVersionNormalizer.normalize(candidate.getTaxYear(), candidate.getRuleVersion());
        if (!canonicalRuleVersion.equals(candidate.getRuleVersion())) {
            throw new BusinessException(
                ErrorCode.RULE_SET_PUBLISH_NOT_ALLOWED,
                "Only canonical ruleVersion values can be published."
            );
        }
        if (candidate.getGitCommitSha() == null || candidate.getGitCommitSha().isBlank()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_PUBLISH_NOT_ALLOWED,
                "A reviewed rule set must keep the canonical Git commit SHA before publication."
            );
        }

        List<DeductionRule> candidateRules = deductionRuleRepository.findAllByTaxRuleSetId(ruleSetId);
        if (candidateRules.isEmpty()) {
            throw new BusinessException(
                ErrorCode.RULE_SET_PUBLISH_NOT_ALLOWED,
                "A reviewed rule set must project at least one deduction rule before publication."
            );
        }

        candidate.setRuleSnapshotHash(ruleSnapshotHasher.hash(candidate.getTaxYear(), candidate.getRuleVersion(), candidateRules));

        UUID previousPublishedRuleSetId = taxRuleSetRepository
            .findFirstByTaxYearAndStatusOrderByPublishedAtDesc(candidate.getTaxYear(), RuleSetStatus.PUBLISHED)
            .map(previousPublished -> {
                retirePublishedRuleSet(previousPublished);
                return previousPublished.getId();
            })
            .orElse(null);

        activateRules(candidateRules, true);
        candidate.setPreviousPublishedRuleSetId(previousPublishedRuleSetId);
        candidate.setStatus(RuleSetStatus.PUBLISHED);
        candidate.setPublishedBy(publisherEmail);
        candidate.setPublishedAt(OffsetDateTime.now());

        return toResponse(candidate);
    }

    private TaxRuleSet getRuleSet(UUID ruleSetId) {
        return taxRuleSetRepository.findById(ruleSetId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RULE_SET_NOT_FOUND));
    }

    private void retirePublishedRuleSet(TaxRuleSet previousPublished) {
        previousPublished.setStatus(RuleSetStatus.RETIRED);
        activateRules(deductionRuleRepository.findAllByTaxRuleSetId(previousPublished.getId()), false);
    }

    private void activateRules(List<DeductionRule> rules, boolean active) {
        rules.forEach(rule -> rule.setActive(active));
    }

    private String toReviewNotesJson(String reviewerEmail, AdminDtos.ReviewRuleSetRequest request) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision", request.approved() ? "APPROVED" : "REJECTED");
        payload.put("comment", request.comment());
        payload.put("reviewedBy", reviewerEmail);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize rule-set review notes.", exception);
        }
    }

    private AdminDtos.AdminRuleSetResponse toResponse(TaxRuleSet ruleSet) {
        return new AdminDtos.AdminRuleSetResponse(
            ruleSet.getId(),
            ruleSet.getTaxYear(),
            ruleSet.getRuleVersion(),
            ruleSet.getStatus(),
            ruleSet.getHumanReviewStatus(),
            ruleSet.getRuleSnapshotHash(),
            ruleSet.getGitCommitSha(),
            ruleSet.getReviewedBy(),
            ruleSet.getReviewedAt(),
            ruleSet.getPublishedBy(),
            ruleSet.getPublishedAt()
        );
    }
}
