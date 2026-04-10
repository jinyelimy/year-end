package com.example.yearend.deduction.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "tax_rule_sets")
public class TaxRuleSet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer taxYear;

    @Column(nullable = false, length = 20)
    private String ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleSetStatus status = RuleSetStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HumanReviewStatus humanReviewStatus = HumanReviewStatus.PENDING;

    @Column(length = 255)
    private String canonicalPackPath;

    @Column(length = 64)
    private String gitCommitSha;

    @Column(length = 120)
    private String reviewedBy;

    private OffsetDateTime reviewedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String reviewNotesJsonb = "{}";

    @Column(length = 120)
    private String publishedBy;

    private OffsetDateTime publishedAt;

    private UUID previousPublishedRuleSetId;

    private UUID rollbackFromRuleSetId;

    @Column(length = 64)
    private String ruleSnapshotHash;

    @PrePersist
    public void prePersist() {
        if (reviewNotesJsonb == null) {
            reviewNotesJsonb = "{}";
        }
    }
}
