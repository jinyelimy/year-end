package com.example.yearend.deduction.domain;

import com.example.yearend.common.persistence.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "deduction_rules")
public class DeductionRule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer taxYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_rule_set_id")
    private TaxRuleSet taxRuleSet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DeductionType deductionType;

    @Column(nullable = false)
    private String ruleCode;

    @Column(nullable = false)
    private String ruleName;

    @Column(nullable = false)
    private Integer ruleVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RuleCategory ruleCategory;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String parameterJsonb = "{}";

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean isActive = true;
}
