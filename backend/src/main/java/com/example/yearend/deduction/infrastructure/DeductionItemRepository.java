package com.example.yearend.deduction.infrastructure;

import com.example.yearend.deduction.domain.DeductionItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeductionItemRepository extends JpaRepository<DeductionItem, UUID> {

    List<DeductionItem> findAllByTaxSessionIdAndDeletedAtIsNull(UUID taxSessionId);

    Optional<DeductionItem> findByIdAndTaxSessionIdAndDeletedAtIsNull(UUID id, UUID taxSessionId);
}
