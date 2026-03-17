package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.document.domain.DocumentChecklist;
import com.example.yearend.document.domain.ReviewStatus;
import com.example.yearend.document.infrastructure.DocumentChecklistRepository;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.taxsession.infrastructure.TaxSessionRepository;
import com.example.yearend.user.application.UserService;
import com.example.yearend.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminReviewService {

    private final TaxSessionRepository taxSessionRepository;
    private final TaxSessionService taxSessionService;
    private final DocumentChecklistRepository documentChecklistRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminSessionResponse> listSessions(SessionStatus status) {
        SessionStatus resolvedStatus = status == null ? SessionStatus.SUBMITTED : status;
        return taxSessionRepository.findAllBySessionStatusAndDeletedAtIsNullOrderByCreatedAtDesc(resolvedStatus).stream()
            .map(this::toSessionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminChecklistResponse> listChecklists(UUID sessionId) {
        taxSessionService.getSession(sessionId);
        return documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(sessionId).stream()
            .map(this::toChecklistResponse)
            .toList();
    }

    @Transactional
    public AdminDtos.AdminChecklistResponse reviewChecklist(
        String reviewerEmail,
        UUID checklistId,
        AdminDtos.ReviewChecklistRequest request
    ) {
        DocumentChecklist checklist = documentChecklistRepository.findById(checklistId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CHECKLIST_NOT_FOUND));
        if (!checklist.isRequiredYn()) {
            throw new BusinessException(ErrorCode.ADMIN_REVIEW_NOT_ALLOWED);
        }

        User reviewer = userService.getByEmail(reviewerEmail);
        checklist.setReviewStatus(request.reviewStatus());
        checklist.setComment(request.comment());
        checklist.setReviewedAt(OffsetDateTime.now());
        checklist.setReviewedBy(reviewer);

        TaxSession session = checklist.getTaxSession();
        updateSessionStatus(session);

        return toChecklistResponse(checklist);
    }

    private void updateSessionStatus(TaxSession session) {
        List<DocumentChecklist> checklists = documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(session.getId());
        boolean hasRejected = checklists.stream().anyMatch(checklist -> checklist.getReviewStatus() == ReviewStatus.REJECTED);
        boolean allApproved = !checklists.isEmpty() && checklists.stream().allMatch(checklist -> checklist.getReviewStatus() == ReviewStatus.APPROVED);

        if (hasRejected) {
            session.setSessionStatus(SessionStatus.REJECTED);
            return;
        }
        if (allApproved) {
            session.setSessionStatus(SessionStatus.REVIEWED);
        }
    }

    private AdminDtos.AdminSessionResponse toSessionResponse(TaxSession session) {
        return new AdminDtos.AdminSessionResponse(
            session.getId(),
            session.getUser().getId(),
            session.getUser().getName(),
            session.getTaxYear(),
            session.getSessionStatus(),
            session.getSubmittedAt()
        );
    }

    private AdminDtos.AdminChecklistResponse toChecklistResponse(DocumentChecklist checklist) {
        return new AdminDtos.AdminChecklistResponse(
            checklist.getId(),
            checklist.getTaxSession().getId(),
            checklist.getDeductionItem() == null ? null : checklist.getDeductionItem().getId(),
            checklist.getDocumentType(),
            checklist.isRequiredYn(),
            checklist.isSubmittedYn(),
            checklist.getReviewStatus(),
            checklist.getComment(),
            checklist.getReviewedAt(),
            checklist.getReviewedBy() == null ? null : checklist.getReviewedBy().getId()
        );
    }
}
