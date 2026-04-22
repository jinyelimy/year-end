package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxRuleSetRepository extends JpaRepository<TaxRuleSet, UUID> {

    Optional<TaxRuleSet> findFirstByTaxYearAndRuleVersionAndStatusOrderByPublishedAtDesc(
        Integer taxYear,
        String ruleVersion,
        RuleSetStatus status
    );

    Optional<TaxRuleSet> findFirstByTaxYearAndStatusOrderByPublishedAtDesc(Integer taxYear, RuleSetStatus status);

    Optional<TaxRuleSet> findFirstByTaxYearAndRuleVersion(Integer taxYear, String ruleVersion);
}
