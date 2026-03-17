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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "dependents")
public class Dependent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_session_id", nullable = false)
    private TaxSession taxSession;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RelationType relationType;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false)
    private Long annualIncomeAmount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResidentType residentType = ResidentType.RESIDENT;

    @Column(nullable = false)
    private boolean livesTogether;

    @Column(nullable = false)
    private boolean isDisabled;

    @Column(nullable = false)
    private boolean isBasicDeductionTarget;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String attributesJsonb = "{}";

    private OffsetDateTime deletedAt;
}
