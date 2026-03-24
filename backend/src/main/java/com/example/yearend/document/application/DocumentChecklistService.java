package com.example.yearend.document.application;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.document.api.DocumentChecklistDtos;
import com.example.yearend.document.domain.DocumentChecklist;
import com.example.yearend.document.domain.DocumentType;
import com.example.yearend.document.domain.ReviewStatus;
import com.example.yearend.document.infrastructure.DocumentChecklistRepository;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.TaxSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentChecklistService {

    private final TaxSessionService taxSessionService;
    private final DocumentChecklistRepository documentChecklistRepository;

    @Transactional(readOnly = true)
    public List<DocumentChecklistDtos.ChecklistResponse> list(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        return documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(sessionId).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public void synchronize(TaxSession session, List<DeductionItem> deductionItems) {
        pruneObsolete(session.getId(), deductionItems);

        for (DeductionItem item : deductionItems) {
            DocumentType documentType = resolveDocumentType(item.getDeductionType());
            if (documentType == null) {
                continue;
            }

            DocumentChecklist checklist = documentChecklistRepository
                .findByTaxSessionIdAndDeductionItemId(session.getId(), item.getId())
                .orElseGet(DocumentChecklist::new);

            checklist.setTaxSession(session);
            checklist.setDeductionItem(item);
            checklist.setDocumentType(documentType);
            checklist.setRequiredYn(true);
            checklist.setSubmittedYn(item.getEvidenceStatus() != EvidenceStatus.PENDING);
            if (checklist.getReviewStatus() == null) {
                checklist.setReviewStatus(ReviewStatus.PENDING);
            }
            documentChecklistRepository.save(checklist);
        }
    }

    private void pruneObsolete(UUID sessionId, List<DeductionItem> deductionItems) {
        Set<UUID> activeDeductionIds = deductionItems.stream()
            .map(DeductionItem::getId)
            .collect(java.util.stream.Collectors.toSet());

        List<DocumentChecklist> obsolete = documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(sessionId)
            .stream()
            .filter(checklist -> checklist.getDeductionItem() != null)
            .filter(checklist -> !activeDeductionIds.contains(checklist.getDeductionItem().getId()))
            .toList();

        if (!obsolete.isEmpty()) {
            documentChecklistRepository.deleteAll(obsolete);
        }
    }

    public DocumentChecklistDtos.ChecklistResponse toResponse(DocumentChecklist checklist) {
        return new DocumentChecklistDtos.ChecklistResponse(
            checklist.getId(),
            checklist.getDeductionItem() == null ? null : checklist.getDeductionItem().getId(),
            checklist.getDocumentType(),
            checklist.isRequiredYn(),
            checklist.isSubmittedYn(),
            checklist.getReviewStatus(),
            checklist.getComment(),
            checklist.getReviewedAt()
        );
    }

    private DocumentType resolveDocumentType(DeductionType deductionType) {
        return switch (deductionType) {
            case MEDICAL_EXPENSE -> DocumentType.MEDICAL_RECEIPT;
            case EDUCATION_EXPENSE -> DocumentType.EDUCATION_RECEIPT;
            case DONATION -> DocumentType.DONATION_RECEIPT;
            case INSURANCE -> DocumentType.INSURANCE_STATEMENT;
            default -> null;
        };
    }
}
