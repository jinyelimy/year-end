package com.example.yearend.taxsession.infrastructure;

import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxSessionRepository extends JpaRepository<TaxSession, UUID> {

    Optional<TaxSession> findByIdAndDeletedAtIsNull(UUID id);

    Optional<TaxSession> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    List<TaxSession> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    List<TaxSession> findAllBySessionStatusAndDeletedAtIsNullOrderByCreatedAtDesc(SessionStatus sessionStatus);
}
