package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import com.example.yearend.deduction.infrastructure.DeductionRuleRepository;
import com.example.yearend.deduction.infrastructure.TaxRuleSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleSetResolver {

    private final TaxRuleSetRepository taxRuleSetRepository;
    private final DeductionRuleRepository deductionRuleRepository;
    private final RuleVersionNormalizer ruleVersionNormalizer;
    private final RuleSnapshotHasher ruleSnapshotHasher;

    public RuleSetSnapshot resolve(int taxYear, String requestedRuleVersion, LocalDate calculationDate) {
        String canonicalRuleVersion = ruleVersionNormalizer.normalize(taxYear, requestedRuleVersion);
        TaxRuleSet publishedRuleSet = resolvePublishedRuleSet(taxYear, requestedRuleVersion, canonicalRuleVersion);
        List<DeductionRule> activeRules = deductionRuleRepository
            .findAllByTaxRuleSetIdAndIsActiveTrueOrderByEffectiveFromAscRuleCodeAsc(publishedRuleSet.getId());
        List<DeductionRule> applicableRules = activeRules.stream()
            .filter(rule -> isEffectiveOn(rule, calculationDate))
            .sorted(ruleComparator())
            .toList();

        return new RuleSetSnapshot(
            publishedRuleSet.getId().toString(),
            publishedRuleSet.getRuleVersion(),
            resolveSnapshotHash(publishedRuleSet, applicableRules),
            true,
            applicableRules
        );
    }

    private TaxRuleSet resolvePublishedRuleSet(int taxYear, String requestedRuleVersion, String canonicalRuleVersion) {
        if (requestedRuleVersion == null || requestedRuleVersion.isBlank()) {
            return taxRuleSetRepository.findFirstByTaxYearAndStatusOrderByPublishedAtDesc(taxYear, RuleSetStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.PUBLISHED_RULE_SET_NOT_FOUND,
                    "No published rule set exists for tax year " + taxYear + "."
                ));
        }

        return taxRuleSetRepository
            .findFirstByTaxYearAndRuleVersionAndStatusOrderByPublishedAtDesc(
                taxYear,
                canonicalRuleVersion,
                RuleSetStatus.PUBLISHED
            )
            .orElseThrow(() -> new BusinessException(
                ErrorCode.PUBLISHED_RULE_SET_NOT_FOUND,
                "No published rule set exists for tax year " + taxYear + " and rule version " + canonicalRuleVersion + "."
            ));
    }

    private boolean isEffectiveOn(DeductionRule rule, LocalDate calculationDate) {
        boolean startsOnOrBefore = !rule.getEffectiveFrom().isAfter(calculationDate);
        boolean endsOnOrAfter = rule.getEffectiveTo() == null || !rule.getEffectiveTo().isBefore(calculationDate);
        return startsOnOrBefore && endsOnOrAfter;
    }

    private Comparator<DeductionRule> ruleComparator() {
        return Comparator
            .comparing(DeductionRule::getEffectiveFrom)
            .thenComparing(rule -> rule.getDeductionType().name())
            .thenComparing(DeductionRule::getRuleCode);
    }

    private String resolveSnapshotHash(TaxRuleSet publishedRuleSet, List<DeductionRule> applicableRules) {
        if (publishedRuleSet.getRuleSnapshotHash() != null && !publishedRuleSet.getRuleSnapshotHash().isBlank()) {
            return publishedRuleSet.getRuleSnapshotHash();
        }
        return ruleSnapshotHasher.hash(
            publishedRuleSet.getTaxYear(),
            publishedRuleSet.getRuleVersion(),
            applicableRules
        );
    }
}
