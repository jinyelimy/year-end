package com.example.yearend.taxsession.infrastructure;

import com.example.yearend.taxsession.domain.IncomeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncomeItemRepository extends JpaRepository<IncomeItem, UUID> {

    List<IncomeItem> findAllByTaxSessionIdAndDeletedAtIsNull(UUID taxSessionId);

    Optional<IncomeItem> findByIdAndTaxSessionIdAndDeletedAtIsNull(UUID id, UUID taxSessionId);
}
