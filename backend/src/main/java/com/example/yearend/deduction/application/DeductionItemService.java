package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.api.DeductionItemDtos;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.deduction.infrastructure.DeductionItemRepository;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.TaxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeductionItemService {

    private final TaxSessionService taxSessionService;
    private final DependentService dependentService;
    private final DeductionItemRepository deductionItemRepository;
    private final DocumentChecklistService documentChecklistService;
    private final HometaxPdfImportParser hometaxPdfImportParser;
    private final DeductionItemReviewPolicy deductionItemReviewPolicy;
    private final ObjectMapper objectMapper;

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
        synchronizeDocuments(session);
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
        synchronizeDocuments(item.getTaxSession());
        return toResponse(item);
    }

    @Transactional
    public void delete(String email, UUID sessionId, UUID deductionItemId) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        DeductionItem item = deductionItemRepository.findByIdAndTaxSessionIdAndDeletedAtIsNull(deductionItemId, sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.DEDUCTION_ITEM_NOT_FOUND));
        item.setDeletedAt(OffsetDateTime.now());
        synchronizeDocuments(session);
    }

    @Transactional
    public DeductionItemDtos.HometaxImportResponse importHometax(String email, UUID sessionId, MultipartFile file) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename())
            ? file.getOriginalFilename()
            : "hometax-import.pdf";

        clearImportedItems(sessionId);

        UUID importBatchId = UUID.randomUUID();
        OffsetDateTime importedAt = OffsetDateTime.now();
        ParsedHometaxDocument parsedDocument = hometaxPdfImportParser.parse(session, file, fileName, importedAt);

        List<DeductionItem> createdItems = parsedDocument.candidates().stream()
            .map(candidate -> createImportedItem(session, importBatchId, parsedDocument, candidate))
            .toList();

        synchronizeDocuments(session);

        List<DeductionItemDtos.DeductionItemResponse> responses = createdItems.stream()
            .map(this::toResponse)
            .toList();

        int autoAppliedCount = (int) parsedDocument.candidates().stream()
            .filter(ParsedDeductionCandidate::isAutoApplied)
            .count();
        int needsReviewCount = parsedDocument.candidates().size() - autoAppliedCount;

        return new DeductionItemDtos.HometaxImportResponse(
            importBatchId,
            fileName,
            importedAt,
            responses.size(),
            autoAppliedCount,
            needsReviewCount,
            responses
        );
    }

    @Transactional(readOnly = true)
    public List<DeductionItem> getEntities(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId);
    }

    @Transactional(readOnly = true)
    public List<DeductionItem> getCalculationEligibleEntities(String email, UUID sessionId) {
        return getEntities(email, sessionId).stream()
            .filter(deductionItemReviewPolicy::isIncludedInCalculation)
            .toList();
    }

    private DeductionItem createImportedItem(
        TaxSession session,
        UUID importBatchId,
        ParsedHometaxDocument parsedDocument,
        ParsedDeductionCandidate candidate
    ) {
        DeductionItem item = new DeductionItem();
        item.setTaxSession(session);
        item.setDeductionType(candidate.deductionType());
        item.setDependent(null);
        item.setSubType(candidate.subType());
        item.setAmount(candidate.amount());
        item.setUsedAt(candidate.usedAt());
        item.setSourceName(candidate.sourceName());
        item.setEvidenceStatus(candidate.evidenceStatus());
        item.setAttributesJsonb(writeAttributes(buildImportedAttributes(importBatchId, parsedDocument, candidate)));
        deductionItemRepository.save(item);
        return item;
    }

    private Map<String, Object> buildImportedAttributes(
        UUID importBatchId,
        ParsedHometaxDocument parsedDocument,
        ParsedDeductionCandidate candidate
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sourceType", "HOMETAX");
        attributes.put("sourceLabel", "Hometax PDF");
        attributes.put("entryChannel", "IMPORT_SYNC");
        attributes.put("importBatchId", importBatchId.toString());
        attributes.put("importFileName", parsedDocument.fileName());
        attributes.put("importedAt", parsedDocument.parsedAt().toString());
        attributes.put("importBucket", candidate.reviewDecision().importBucket());
        attributes.put("reviewStatus", candidate.reviewDecision().reviewStatus());
        attributes.put("confidenceLevel", candidate.reviewDecision().confidenceLevel());
        attributes.put("reviewReason", candidate.reviewDecision().reviewReason());
        attributes.put("parserType", parsedDocument.parserType());
        attributes.put("textLayerDetected", parsedDocument.textLayerDetected());
        attributes.put("parsingWarnings", parsedDocument.warnings());
        attributes.put("pageNumber", candidate.pageNumber());
        attributes.put("rawSectionTitle", candidate.rawSectionTitle());
        attributes.put("rawLineText", candidate.rawLineText());
        return attributes;
    }

    private void clearImportedItems(UUID sessionId) {
        deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId).stream()
            .filter(this::isImportedItem)
            .forEach(item -> item.setDeletedAt(OffsetDateTime.now()));
    }

    private boolean isImportedItem(DeductionItem item) {
        return deductionItemReviewPolicy.isImported(item);
    }

    private void synchronizeDocuments(TaxSession session) {
        List<DeductionItem> activeItems = deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(session.getId()).stream()
            .filter(this::isIncludedInDocuments)
            .toList();
        documentChecklistService.synchronize(session, activeItems);
    }

    private boolean isIncludedInDocuments(DeductionItem item) {
        return deductionItemReviewPolicy.isIncludedInDocumentChecklist(item);
    }

    private String writeAttributes(Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(attributes));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize deduction attributes.", exception);
        }
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
