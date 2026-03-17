package com.example.yearend.taxsession.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.taxsession.api.DependentDtos;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.ResidentType;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.taxsession.infrastructure.DependentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DependentService {

    private final TaxSessionService taxSessionService;
    private final DependentRepository dependentRepository;

    @Transactional
    public DependentDtos.DependentResponse create(
        String email,
        UUID sessionId,
        DependentDtos.UpsertDependentRequest request
    ) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        Dependent dependent = new Dependent();
        dependent.setTaxSession(session);
        apply(dependent, request);
        dependentRepository.save(dependent);
        return toResponse(dependent);
    }

    @Transactional(readOnly = true)
    public List<DependentDtos.DependentResponse> list(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return dependentRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Dependent> getEntities(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return dependentRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId);
    }

    @Transactional
    public DependentDtos.DependentResponse update(
        String email,
        UUID sessionId,
        UUID dependentId,
        DependentDtos.UpsertDependentRequest request
    ) {
        taxSessionService.getOwnedSession(email, sessionId);
        Dependent dependent = dependentRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(dependentId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPENDENT_NOT_FOUND));
        apply(dependent, request);
        return toResponse(dependent);
    }

    @Transactional
    public void delete(String email, UUID sessionId, UUID dependentId) {
        taxSessionService.getOwnedSession(email, sessionId);
        Dependent dependent = dependentRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(dependentId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPENDENT_NOT_FOUND));
        dependent.setDeletedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public Dependent getEntity(UUID sessionId, UUID dependentId) {
        return dependentRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(dependentId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEPENDENT_NOT_FOUND));
    }

    private void apply(Dependent dependent, DependentDtos.UpsertDependentRequest request) {
        dependent.setName(request.name().trim());
        dependent.setRelationType(request.relationType());
        dependent.setBirthDate(request.birthDate());
        dependent.setAnnualIncomeAmount(request.annualIncomeAmount());
        dependent.setResidentType(Objects.requireNonNullElse(request.residentType(), ResidentType.RESIDENT));
        dependent.setLivesTogether(request.livesTogether());
        dependent.setDisabled(request.disabled());
        dependent.setBasicDeductionTarget(request.basicDeductionTarget());
    }

    private DependentDtos.DependentResponse toResponse(Dependent dependent) {
        return new DependentDtos.DependentResponse(
            dependent.getId(),
            dependent.getName(),
            dependent.getRelationType(),
            dependent.getBirthDate(),
            dependent.getAnnualIncomeAmount(),
            dependent.getResidentType(),
            dependent.isLivesTogether(),
            dependent.isDisabled(),
            dependent.isBasicDeductionTarget()
        );
    }
}
