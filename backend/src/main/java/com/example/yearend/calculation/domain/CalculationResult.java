package com.example.yearend.calculation.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
import com.example.yearend.taxsession.domain.TaxSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "calculation_results")
public class CalculationResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_session_id", nullable = false)
    private TaxSession taxSession;

    @Column(nullable = false)
    private Integer calculationVersion;

    @Column(nullable = false)
    private String ruleVersion;

    @Column
    private String ruleSetId;

    @Column
    private String ruleSnapshotHash;

    @Column(nullable = false)
    private String inputHash;

    @Column(nullable = false)
    private Long totalIncomeAmount;

    @Column(nullable = false)
    private Long totalDeductionAmount;

    @Column(nullable = false)
    private Long taxableIncomeAmount;

    @Column(nullable = false)
    private Long calculatedTaxAmount;

    @Column(nullable = false)
    private Long taxCreditAmount;

    @Column(nullable = false)
    private Long finalTaxAmount;

    @Column(nullable = false)
    private Long withholdingTaxAmount;

    @Column(nullable = false)
    private Long expectedRefundAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String decisionTraceJsonb = "[]";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String summaryJsonb = "{}";

    @PrePersist
    public void prePersist() {
        if (decisionTraceJsonb == null) {
            decisionTraceJsonb = "[]";
        }
        if (summaryJsonb == null) {
            summaryJsonb = "{}";
        }
    }
}
