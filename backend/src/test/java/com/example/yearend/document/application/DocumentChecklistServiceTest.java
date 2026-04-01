package com.example.yearend.document.application;

import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.document.domain.DocumentChecklist;
import com.example.yearend.document.domain.DocumentType;
import com.example.yearend.document.domain.ReviewStatus;
import com.example.yearend.document.infrastructure.DocumentChecklistRepository;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.TaxSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentChecklistServiceTest {

    @Mock
    private TaxSessionService taxSessionService;

    @Mock
    private DocumentChecklistRepository documentChecklistRepository;

    private DocumentChecklistService documentChecklistService;

    @BeforeEach
    void setUp() {
        documentChecklistService = new DocumentChecklistService(taxSessionService, documentChecklistRepository);
    }

    @Test
    @DisplayName("creates a checklist entry for an insurance deduction item")
    void createsChecklistForInsuranceItem() {
        TaxSession session = taxSession();
        DeductionItem insuranceItem = deductionItem(DeductionType.INSURANCE, EvidenceStatus.SUBMITTED);

        when(documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());
        when(documentChecklistRepository.findByTaxSessionIdAndDeductionItemId(session.getId(), insuranceItem.getId()))
            .thenReturn(Optional.empty());

        documentChecklistService.synchronize(session, List.of(insuranceItem));

        ArgumentCaptor<DocumentChecklist> captor = ArgumentCaptor.forClass(DocumentChecklist.class);
        verify(documentChecklistRepository).save(captor.capture());

        DocumentChecklist saved = captor.getValue();
        assertThat(saved.getTaxSession()).isEqualTo(session);
        assertThat(saved.getDeductionItem()).isEqualTo(insuranceItem);
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.INSURANCE_STATEMENT);
        assertThat(saved.isRequiredYn()).isTrue();
        assertThat(saved.isSubmittedYn()).isTrue();
        assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    @DisplayName("does not create a checklist entry for credit card deduction items")
    void skipsChecklistForCreditCardItem() {
        TaxSession session = taxSession();
        DeductionItem creditCardItem = deductionItem(DeductionType.CREDIT_CARD, EvidenceStatus.SUBMITTED);

        when(documentChecklistRepository.findAllByTaxSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());

        documentChecklistService.synchronize(session, List.of(creditCardItem));

        verify(documentChecklistRepository, never()).save(any());
    }

    private TaxSession taxSession() {
        TaxSession session = new TaxSession();
        session.setId(UUID.randomUUID());
        session.setTaxYear(2025);
        return session;
    }

    private DeductionItem deductionItem(DeductionType deductionType, EvidenceStatus evidenceStatus) {
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(deductionType);
        item.setAmount(100_000L);
        item.setEvidenceStatus(evidenceStatus);
        item.setAttributesJsonb("{}");
        return item;
    }
}
