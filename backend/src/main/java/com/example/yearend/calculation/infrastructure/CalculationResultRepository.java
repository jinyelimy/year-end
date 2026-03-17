package com.example.yearend.calculation.infrastructure;

import com.example.yearend.calculation.domain.CalculationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalculationResultRepository extends JpaRepository<CalculationResult, UUID> {

    List<CalculationResult> findAllByTaxSessionIdOrderByCalculationVersionDesc(UUID taxSessionId);

    Optional<CalculationResult> findFirstByTaxSessionIdOrderByCalculationVersionDesc(UUID taxSessionId);
}
