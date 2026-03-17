package com.example.yearend.taxsession.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.taxsession.api.IncomeItemDtos;
import com.example.yearend.taxsession.domain.IncomeItem;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.taxsession.infrastructure.IncomeItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncomeItemService {

    private final TaxSessionService taxSessionService;
    private final IncomeItemRepository incomeItemRepository;

    @Transactional
    public IncomeItemDtos.IncomeItemResponse create(
        String email,
        UUID sessionId,
        IncomeItemDtos.UpsertIncomeItemRequest request
    ) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        IncomeItem item = new IncomeItem();
        item.setTaxSession(session);
        apply(item, request);
        incomeItemRepository.save(item);
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<IncomeItemDtos.IncomeItemResponse> list(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return incomeItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public IncomeItemDtos.IncomeItemResponse update(
        String email,
        UUID sessionId,
        UUID incomeItemId,
        IncomeItemDtos.UpsertIncomeItemRequest request
    ) {
        taxSessionService.getOwnedSession(email, sessionId);
        IncomeItem item = incomeItemRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(incomeItemId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INCOME_ITEM_NOT_FOUND));
        apply(item, request);
        return toResponse(item);
    }

    @Transactional
    public void delete(String email, UUID sessionId, UUID incomeItemId) {
        taxSessionService.getOwnedSession(email, sessionId);
        IncomeItem item = incomeItemRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(incomeItemId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.INCOME_ITEM_NOT_FOUND));
        item.setDeletedAt(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<IncomeItem> getEntities(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return incomeItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId);
    }

    private void apply(IncomeItem item, IncomeItemDtos.UpsertIncomeItemRequest request) {
        item.setIncomeType(request.incomeType());
        item.setPayerName(request.payerName());
        item.setGrossAmount(request.grossAmount());
        item.setTaxableAmount(request.taxableAmount());
        item.setWithheldTaxAmount(request.withheldTaxAmount());
        item.setNonTaxableAmount(request.nonTaxableAmount());
        item.setAttributesJsonb(Objects.requireNonNullElse(request.attributesJsonb(), "{}"));
    }

    private IncomeItemDtos.IncomeItemResponse toResponse(IncomeItem item) {
        return new IncomeItemDtos.IncomeItemResponse(
            item.getId(),
            item.getIncomeType(),
            item.getPayerName(),
            item.getGrossAmount(),
            item.getTaxableAmount(),
            item.getWithheldTaxAmount(),
            item.getNonTaxableAmount(),
            item.getAttributesJsonb()
        );
    }
}
