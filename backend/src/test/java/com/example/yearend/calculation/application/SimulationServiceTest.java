package com.example.yearend.calculation.application;

import com.example.yearend.calculation.domain.TaxCalculationOutcome;
import com.example.yearend.calculation.domain.TaxCalculationService;
import com.example.yearend.calculation.infrastructure.CalculationResultRepository;
import com.example.yearend.deduction.application.DeductionEngine;
import com.example.yearend.deduction.application.DeductionItemReviewPolicy;
import com.example.yearend.deduction.application.DeductionItemService;
import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.IncomeItemService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.IncomeItem;
import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private TaxSessionService taxSessionService;

    @Mock
    private IncomeItemService incomeItemService;

    @Mock
    private DependentService dependentService;

    @Mock
    private DeductionItemService deductionItemService;

    @Mock
    private DeductionEngine deductionEngine;

    @Mock
    private TaxCalculationService taxCalculationService;

    @Mock
    private CalculationResultRepository calculationResultRepository;

    @Mock
    private DocumentChecklistService documentChecklistService;

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(
            taxSessionService,
            incomeItemService,
            dependentService,
            deductionItemService,
            new DeductionItemReviewPolicy(new ObjectMapper()),
            deductionEngine,
            taxCalculationService,
            calculationResultRepository,
            documentChecklistService,
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("run uses only calculation eligible deduction items and synchronizes the same list")
    @SuppressWarnings("unchecked")
    void runUsesCalculationEligibleItems() {
        String email = "tester@example.com";
        UUID sessionId = UUID.randomUUID();
        TaxSession session = taxSession(sessionId);
        DeductionItem approvedImported = deductionItem(
            DeductionType.INSURANCE,
            2_545_170L,
            """
                {"sourceType":"HOMETAX","reviewStatus":"APPROVED"}
                """
        );
        DeductionItem manualMedical = deductionItem(DeductionType.MEDICAL_EXPENSE, 480_000L, "{}");
        List<DeductionItem> eligibleItems = List.of(approvedImported, manualMedical);
        List<DeductionDecision> decisions = List.of(
            new DeductionDecision(approvedImported.getId(), DeductionType.INSURANCE, true, 2_545_170L, 0L, 0L, 120_000L, List.of()),
            new DeductionDecision(manualMedical.getId(), DeductionType.MEDICAL_EXPENSE, true, 480_000L, 480_000L, 480_000L, 0L, List.of())
        );

        when(taxSessionService.getOwnedSession(email, sessionId)).thenReturn(session);
        when(deductionItemService.getCalculationEligibleEntities(email, sessionId)).thenReturn(eligibleItems);
        when(incomeItemService.getEntities(email, sessionId)).thenReturn(List.of(incomeItem(session)));
        when(dependentService.getEntities(email, sessionId)).thenReturn(List.of());
        when(deductionEngine.evaluate(any(), eq(eligibleItems))).thenReturn(decisions);
        when(taxCalculationService.calculate(any())).thenReturn(
            new TaxCalculationOutcome(
                50_000_000L,
                480_000L,
                49_520_000L,
                2_971_200L,
                120_000L,
                2_851_200L,
                3_000_000L,
                148_800L,
                List.of("ok")
            )
        );
        when(calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId))
            .thenReturn(Optional.empty());

        simulationService.run(email, sessionId);

        verify(deductionItemService).getCalculationEligibleEntities(email, sessionId);

        ArgumentCaptor<List<DeductionItem>> checklistCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentChecklistService).synchronize(eq(session), checklistCaptor.capture());
        assertThat(checklistCaptor.getValue()).containsExactlyElementsOf(eligibleItems);

        verify(taxSessionService).updateStatus(session, SessionStatus.CALCULATED);
    }

    @Test
    @DisplayName("rejections are also calculated only from calculation eligible deduction items")
    void rejectionsUseCalculationEligibleItems() {
        String email = "tester@example.com";
        UUID sessionId = UUID.randomUUID();
        TaxSession session = taxSession(sessionId);
        DeductionItem approvedImported = deductionItem(
            DeductionType.INSURANCE,
            2_545_170L,
            """
                {"sourceType":"HOMETAX","reviewStatus":"APPROVED"}
                """
        );
        List<DeductionItem> eligibleItems = List.of(approvedImported);

        when(taxSessionService.getOwnedSession(email, sessionId)).thenReturn(session);
        when(deductionItemService.getCalculationEligibleEntities(email, sessionId)).thenReturn(eligibleItems);
        when(incomeItemService.getEntities(email, sessionId)).thenReturn(List.of(incomeItem(session)));
        when(dependentService.getEntities(email, sessionId)).thenReturn(List.of());
        when(deductionEngine.evaluate(any(), eq(eligibleItems))).thenReturn(
            List.of(DeductionDecision.rejected(
                approvedImported.getId(),
                DeductionType.INSURANCE,
                approvedImported.getAmount(),
                List.of("검토 필요")
            ))
        );

        var rejections = simulationService.getRejections(email, sessionId);

        assertThat(rejections).hasSize(1);
        assertThat(rejections.getFirst().deductionItemId()).isEqualTo(approvedImported.getId());
        verify(deductionItemService).getCalculationEligibleEntities(email, sessionId);
    }

    private TaxSession taxSession(UUID sessionId) {
        TaxSession session = new TaxSession();
        session.setId(sessionId);
        session.setTaxYear(2025);
        session.setRuleVersion("2025.1");
        return session;
    }

    private IncomeItem incomeItem(TaxSession session) {
        IncomeItem item = new IncomeItem();
        item.setTaxSession(session);
        item.setGrossAmount(50_000_000L);
        item.setTaxableAmount(50_000_000L);
        item.setWithheldTaxAmount(3_000_000L);
        item.setNonTaxableAmount(0L);
        return item;
    }

    private DeductionItem deductionItem(DeductionType deductionType, long amount, String attributesJsonb) {
        DeductionItem item = new DeductionItem();
        item.setId(UUID.randomUUID());
        item.setDeductionType(deductionType);
        item.setAmount(amount);
        item.setEvidenceStatus(EvidenceStatus.SUBMITTED);
        item.setAttributesJsonb(attributesJsonb);
        return item;
    }
}
