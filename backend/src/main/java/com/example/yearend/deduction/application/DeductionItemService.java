package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.api.DeductionItemDtos;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.deduction.infrastructure.DeductionItemRepository;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.TaxSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeductionItemService {

    private final TaxSessionService taxSessionService;
    private final DependentService dependentService;
    private final DeductionItemRepository deductionItemRepository;

    @Transactional
    public DeductionItemDtos.DeductionItemResponse create(
        String email,
        UUID sessionId,
        DeductionItemDtos.UpsertDeductionItemRequest request
    ) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        DeductionItem item = new DeductionItem();
        item.setTaxSession(session);
        apply(item, sessionId, request);
        deductionItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<DeductionItemDtos.DeductionItemResponse> list(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public DeductionItemDtos.DeductionItemResponse update(
        String email,
        UUID sessionId,
        UUID deductionItemId,
        DeductionItemDtos.UpsertDeductionItemRequest request
    ) {
        taxSessionService.getOwnedSession(email, sessionId);
        DeductionItem item = deductionItemRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(deductionItemId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEDUCTION_ITEM_NOT_FOUND));
        apply(item, sessionId, request);
        return toResponse(item);
    }

    @Transactional
    public void delete(String email, UUID sessionId, UUID deductionItemId) {
        taxSessionService.getOwnedSession(email, sessionId);
        DeductionItem item = deductionItemRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(deductionItemId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEDUCTION_ITEM_NOT_FOUND));
        item.setDeletedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<DeductionItem> getEntities(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId);
    }

    private void apply(DeductionItem item, UUID sessionId, DeductionItemDtos.UpsertDeductionItemRequest request) {
        item.setDeductionType(request.deductionType());
        item.setDependent(resolveDependent(sessionId, request.dependentId()));
        item.setSubType(request.subType());
        item.setAmount(request.amount());
        item.setUsedAt(request.usedAt());
        item.setSourceName(request.sourceName());
        item.setEvidenceStatus(Objects.requireNonNullElse(request.evidenceStatus(), EvidenceStatus.PENDING));
        item.setAttributesJsonb(Objects.requireNonNullElse(request.attributesJsonb(), "{}"));
    }

    private Dependent resolveDependent(UUID sessionId, UUID dependentId) {
        if (dependentId == null) {
            return null;
        }
        return dependentService.getEntity(sessionId, dependentId);
    }

    private DeductionItemDtos.DeductionItemResponse toResponse(DeductionItem item) {
        return new DeductionItemDtos.DeductionItemResponse(
            item.getId(),
            item.getDeductionType(),
            item.getDependent() == null ? null : item.getDependent().getId(),
            item.getSubType(),
            item.getAmount(),
            item.getUsedAt(),
            item.getSourceName(),
            item.getEvidenceStatus(),
            item.getAttributesJsonb()
        );
    }
}
