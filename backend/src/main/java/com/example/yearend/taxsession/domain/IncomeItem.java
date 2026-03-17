package com.example.yearend.taxsession.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
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
@Table(name = "income_items")
public class IncomeItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_session_id", nullable = false)
    private TaxSession taxSession;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncomeType incomeType;

    private String payerName;

    @Column(nullable = false)
    private Long grossAmount;

    @Column(nullable = false)
    private Long taxableAmount;

    @Column(nullable = false)
    private Long withheldTaxAmount;

    @Column(nullable = false)
    private Long nonTaxableAmount;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String attributesJsonb = "{}";

    private OffsetDateTime deletedAt;
}
