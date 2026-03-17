package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeductionRuleRepository extends JpaRepository<DeductionRule, UUID> {

    List<DeductionRule> findAllByTaxYearAndDeductionTypeAndIsActiveTrue(Integer taxYear, DeductionType deductionType);
}
