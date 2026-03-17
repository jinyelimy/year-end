package com.example.yearend.document.infrastructure;

import com.example.yearend.document.domain.DocumentChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentChecklistRepository extends JpaRepository<DocumentChecklist, UUID> {

    List<DocumentChecklist> findAllByTaxSessionIdOrderByCreatedAtAsc(UUID taxSessionId);

    Optional<DocumentChecklist> findByTaxSessionIdAndDeductionItemId(UUID taxSessionId, UUID deductionItemId);
}
