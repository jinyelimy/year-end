package com.example.yearend.taxsession.infrastructure;

import com.example.yearend.taxsession.domain.Dependent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DependentRepository extends JpaRepository<Dependent, UUID> {

    List<Dependent> findAllByTaxSessionIdAndDeletedAtIsNull(UUID taxSessionId);

    Optional<Dependent> findByIdAndTaxSessionIdAndDeletedAtIsNull(UUID id, UUID taxSessionId);
}
