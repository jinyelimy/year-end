package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.api.DeductionItemDtos;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.deduction.infrastructure.DeductionItemRepository;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.TaxSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeductionItemService {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES_TYPE = new TypeReference<>() {
    };

    private final TaxSessionService taxSessionService;
    private final DependentService dependentService;
    private final DeductionItemRepository deductionItemRepository;
    private final DocumentChecklistService documentChecklistService;
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
        List<ImportedDeductionTemplate> templates = List.of(
            new ImportedDeductionTemplate(
                com.example.yearend.deduction.domain.DeductionType.MEDICAL_EXPENSE,
                "병원비",
                480_000L,
                LocalDate.of(session.getTaxYear(), 1, 15),
                "서울종합병원",
                EvidenceStatus.SUBMITTED,
                "AUTO_APPLIED",
                "APPROVED",
                "HIGH",
                "표준 의료비 항목으로 바로 매핑됨"
            ),
            new ImportedDeductionTemplate(
                com.example.yearend.deduction.domain.DeductionType.INSURANCE,
                "보장성 보험료",
                1_120_000L,
                LocalDate.of(session.getTaxYear(), 2, 10),
                "한빛생명",
                EvidenceStatus.SUBMITTED,
                "AUTO_APPLIED",
                "APPROVED",
                "HIGH",
                "보험료 항목이 명확해 자동 반영 가능"
            ),
            new ImportedDeductionTemplate(
                com.example.yearend.deduction.domain.DeductionType.EDUCATION_EXPENSE,
                "자녀 교육비",
                2_400_000L,
                LocalDate.of(session.getTaxYear(), 3, 4),
                "미래학원",
                EvidenceStatus.PENDING,
                "NEEDS_REVIEW",
                "PENDING",
                "MEDIUM",
                "부양가족 연결과 교육기관 구분 확인 필요"
            ),
            new ImportedDeductionTemplate(
                com.example.yearend.deduction.domain.DeductionType.DONATION,
                "기부금",
                150_000L,
                LocalDate.of(session.getTaxYear(), 12, 22),
                "따뜻한재단",
                EvidenceStatus.PENDING,
                "NEEDS_REVIEW",
                "PENDING",
                "MEDIUM",
                "지정기부금 여부와 증빙 상태 확인 필요"
            )
        );

        List<DeductionItem> createdItems = templates.stream()
            .map(template -> createImportedItem(session, importBatchId, fileName, importedAt, template))
            .toList();

        synchronizeDocuments(session);

        List<DeductionItemDtos.DeductionItemResponse> responses = createdItems.stream()
            .map(this::toResponse)
            .toList();

        int autoAppliedCount = (int) templates.stream()
            .filter(template -> "AUTO_APPLIED".equals(template.importBucket()))
            .count();
        int needsReviewCount = templates.size() - autoAppliedCount;

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

    private DeductionItem createImportedItem(
        TaxSession session,
        UUID importBatchId,
        String fileName,
        OffsetDateTime importedAt,
        ImportedDeductionTemplate template
    ) {
        DeductionItem item = new DeductionItem();
        item.setTaxSession(session);
        item.setDeductionType(template.deductionType());
        item.setDependent(null);
        item.setSubType(template.subType());
        item.setAmount(template.amount());
        item.setUsedAt(template.usedAt());
        item.setSourceName(template.sourceName());
        item.setEvidenceStatus(template.evidenceStatus());
        item.setAttributesJsonb(writeAttributes(Map.of(
            "sourceType", "HOMETAX",
            "sourceLabel", "홈택스 PDF",
            "entryChannel", "IMPORT_SYNC",
            "importBatchId", importBatchId.toString(),
            "importFileName", fileName,
            "importedAt", importedAt.toString(),
            "importBucket", template.importBucket(),
            "reviewStatus", template.reviewStatus(),
            "confidenceLevel", template.confidenceLevel(),
            "reviewReason", template.reviewReason()
        )));
        deductionItemRepository.save(item);
        return item;
    }

    private void clearImportedItems(UUID sessionId) {
        deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(sessionId).stream()
            .filter(this::isImportedItem)
            .forEach(item -> item.setDeletedAt(OffsetDateTime.now()));
    }

    private boolean isImportedItem(DeductionItem item) {
        return "HOMETAX".equals(readAttributes(item).get("sourceType"));
    }

    private void synchronizeDocuments(TaxSession session) {
        List<DeductionItem> activeItems = deductionItemRepository.findAllByTaxSessionIdAndDeletedAtIsNull(session.getId()).stream()
            .filter(this::isIncludedInDocuments)
            .toList();
        documentChecklistService.synchronize(session, activeItems);
    }

    private boolean isIncludedInDocuments(DeductionItem item) {
        Map<String, Object> attributes = readAttributes(item);
        Object sourceType = attributes.get("sourceType");
        Object reviewStatus = attributes.get("reviewStatus");

        if (!"HOMETAX".equals(sourceType)) {
            return true;
        }

        return !"PENDING".equals(reviewStatus) && !"EXCLUDED".equals(reviewStatus);
    }

    private Map<String, Object> readAttributes(DeductionItem item) {
        try {
            return objectMapper.readValue(
                Objects.requireNonNullElse(item.getAttributesJsonb(), "{}"),
                ATTRIBUTES_TYPE
            );
        } catch (IOException exception) {
            return Map.of();
        }
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

    private record ImportedDeductionTemplate(
        com.example.yearend.deduction.domain.DeductionType deductionType,
        String subType,
        Long amount,
        LocalDate usedAt,
        String sourceName,
        EvidenceStatus evidenceStatus,
        String importBucket,
        String reviewStatus,
        String confidenceLevel,
        String reviewReason
    ) {
    }
}
