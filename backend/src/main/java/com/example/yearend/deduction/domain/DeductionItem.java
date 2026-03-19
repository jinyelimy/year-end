package com.example.yearend.deduction.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
import com.example.yearend.taxsession.domain.Dependent;
import com.example.yearend.taxsession.domain.TaxSession;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "deduction_items")
public class DeductionItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_session_id", nullable = false)
    private TaxSession taxSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dependent_id")
    private Dependent dependent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DeductionType deductionType;

    private String subType;

    @Column(nullable = false)
    private Long amount;

    private LocalDate usedAt;

    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EvidenceStatus evidenceStatus = EvidenceStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String attributesJsonb = "{}";

    private OffsetDateTime deletedAt;
}
