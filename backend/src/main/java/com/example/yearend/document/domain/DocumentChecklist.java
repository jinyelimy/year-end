package com.example.yearend.document.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.taxsession.domain.TaxSession;
import com.example.yearend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "document_checklists")
public class DocumentChecklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_session_id", nullable = false)
    private TaxSession taxSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deduction_item_id")
    private DeductionItem deductionItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DocumentType documentType;

    @Column(nullable = false)
    private boolean requiredYn;

    @Column(nullable = false)
    private boolean submittedYn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private OffsetDateTime reviewedAt;

    @Column(columnDefinition = "text")
    private String comment;
}
