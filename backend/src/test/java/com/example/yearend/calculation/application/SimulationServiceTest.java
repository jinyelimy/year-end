package com.example.yearend.calculation.application;

import com.example.yearend.calculation.domain.CalculationResult;
import com.example.yearend.calculation.domain.TaxCalculationCommand;
import com.example.yearend.calculation.domain.TaxCalculationOutcome;
import com.example.yearend.calculation.domain.TaxCalculationService;
import com.example.yearend.calculation.infrastructure.CalculationResultRepository;
import com.example.yearend.deduction.application.DeductionEngine;
import com.example.yearend.deduction.application.DeductionItemReviewPolicy;
import com.example.yearend.deduction.application.DeductionItemService;
import com.example.yearend.deduction.application.RuleSetResolver;
import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.IncomeItemService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.IncomeItem;
import com.example.yearend.taxsession.domain.IncomeType;
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
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
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
    private RuleSetResolver ruleSetResolver;

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
            ruleSetResolver,
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
        RuleSetSnapshot snapshot = new RuleSetSnapshot("2025@2025.01", "2025.01", "abc123", false, List.of());
        List<DeductionDecision> decisions = List.of(
            new DeductionDecision(approvedImported.getId(), DeductionType.INSURANCE, true, 2_545_170L, 0L, 0L, 120_000L, List.of()),
            new DeductionDecision(manualMedical.getId(), DeductionType.MEDICAL_EXPENSE, true, 480_000L, 480_000L, 480_000L, 0L, List.of())
        );

        when(taxSessionService.getOwnedSession(email, sessionId)).thenReturn(session);
        when(deductionItemService.getCalculationEligibleEntities(email, sessionId)).thenReturn(eligibleItems);
        when(ruleSetResolver.resolve(eq(2025), eq("rule-2025.1"), eq(LocalDate.of(2025, 12, 31)))).thenReturn(snapshot);
        when(incomeItemService.getEntities(email, sessionId)).thenReturn(List.of(incomeItem(session)));
        when(dependentService.getEntities(email, sessionId)).thenReturn(List.of());
        when(deductionEngine.evaluate(
            argThat(context -> context.ruleSetSnapshot().ruleVersion().equals("2025.01")),
            eq(eligibleItems)
        )).thenReturn(decisions);
        when(taxCalculationService.calculate(any())).thenReturn(
            new TaxCalculationOutcome(
                37_750_000L,
                50_000_000L,
                0L,
                50_000_000L,
                0L,
                12_250_000L,
                37_750_000L,
                1_500_000L,
                0L,  // pensionInsurancePremiumDeductionAmount
                0L,  // socialInsurancePremiumDeductionAmount
                0L,  // creditCardDeductionAmount
                0L,  // housingLoanDeductionAmount
                0L,  // longTermMortgageDeductionAmount
                0L,  // housingSavingsDeductionAmount
                0L,  // longTermCollectiveInvestmentDeductionAmount
                0L,  // youthLongTermCollectiveInvestmentDeductionAmount
                0L,  // smeMutualAidDeductionAmount
                0L,  // ventureInvestmentDeductionAmount
                0L,  // employeeStockOwnershipDeductionAmount
                480_000L,
                37_270_000L,
                2_236_200L,
                660_000L,
                0L,  // donationTaxCreditAmount
                0L,  // childTaxCreditAmount
                0L,  // pensionAccountTaxCreditAmount
                0L,  // monthlyRentTaxCreditAmount
                0L,  // foreignTaxCreditAmount
                0L,  // taxPayerAssociationCreditAmount
                0L,  // smeYouthEmployeeTaxReductionAmount
                120_000L,
                780_000L,
                1_456_200L,
                3_000_000L,
                1_543_800L,
                List.of("ok")
            )
        );
        when(calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId))
            .thenReturn(Optional.empty());

        var response = simulationService.run(email, sessionId);

        ArgumentCaptor<CalculationResult> resultCaptor = ArgumentCaptor.forClass(CalculationResult.class);
        verify(calculationResultRepository).save(resultCaptor.capture());
        assertThat(resultCaptor.getValue().getRuleVersion()).isEqualTo("2025.01");
        assertThat(resultCaptor.getValue().getRuleSetId()).isEqualTo("2025@2025.01");
        assertThat(resultCaptor.getValue().getRuleSnapshotHash()).isEqualTo("abc123");
        assertThat(resultCaptor.getValue().getSummaryJsonb()).contains("INCOME_TAX_BASIC_BRACKETS_2025");
        assertThat(resultCaptor.getValue().getSummaryJsonb()).contains("earnedIncomeTaxCreditAmount");
        assertThat(resultCaptor.getValue().getSummaryJsonb()).contains("EARNED_INCOME_TAX_CREDIT_BASE_FORMULA_2025");
        assertThat(response.earnedIncomeTaxCreditAmount()).isEqualTo(660_000L);
        assertThat(response.otherTaxCreditAmount()).isEqualTo(120_000L);
        assertThat(response.taxCreditAmount()).isEqualTo(780_000L);
        assertThat(response.personalDeductionAmount()).isEqualTo(1_500_000L);
        assertThat(response.calculationResultId()).isEqualTo(resultCaptor.getValue().getId());

        verify(deductionItemService).getCalculationEligibleEntities(email, sessionId);

        ArgumentCaptor<TaxCalculationCommand> commandCaptor = ArgumentCaptor.forClass(TaxCalculationCommand.class);
        verify(taxCalculationService).calculate(commandCaptor.capture());
        assertThat(commandCaptor.getValue().taxYear()).isEqualTo(2025);
        assertThat(commandCaptor.getValue().totalGrossSalaryAmount()).isEqualTo(50_000_000L);
        assertThat(commandCaptor.getValue().totalNonTaxableIncomeAmount()).isZero();
        assertThat(commandCaptor.getValue().taxableSalaryAmount()).isEqualTo(50_000_000L);
        assertThat(commandCaptor.getValue().dependents()).isEmpty();
        assertThat(commandCaptor.getValue().basicInfoAttributes()).isEmpty();
        assertThat(commandCaptor.getValue().ruleSetSnapshot()).isSameAs(snapshot);

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
        RuleSetSnapshot snapshot = new RuleSetSnapshot("2025@2025.01", "2025.01", "abc123", false, List.of());

        when(taxSessionService.getOwnedSession(email, sessionId)).thenReturn(session);
        when(deductionItemService.getCalculationEligibleEntities(email, sessionId)).thenReturn(eligibleItems);
        when(ruleSetResolver.resolve(eq(2025), eq("rule-2025.1"), eq(LocalDate.of(2025, 12, 31)))).thenReturn(snapshot);
        when(incomeItemService.getEntities(email, sessionId)).thenReturn(List.of(incomeItem(session)));
        when(dependentService.getEntities(email, sessionId)).thenReturn(List.of());
        when(deductionEngine.evaluate(
            argThat(context -> context.ruleSetSnapshot().ruleVersion().equals("2025.01")),
            eq(eligibleItems)
        )).thenReturn(
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

    @Test
    @DisplayName("latest result exposes persisted rule snapshot identifiers")
    void latestResultIncludesRuleSnapshotMetadata() {
        String email = "tester@example.com";
        UUID sessionId = UUID.randomUUID();
        TaxSession session = taxSession(sessionId);
        CalculationResult storedResult = calculationResult(session, "2025.01", "2025@2025.01", "abc123");

        when(taxSessionService.getOwnedSession(email, sessionId)).thenReturn(session);
        when(calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId))
            .thenReturn(Optional.of(storedResult));

        var response = simulationService.getLatestResult(email, sessionId);

        assertThat(response.ruleVersion()).isEqualTo("2025.01");
        assertThat(response.ruleSetId()).isEqualTo("2025@2025.01");
        assertThat(response.ruleSnapshotHash()).isEqualTo("abc123");
    }

    private TaxSession taxSession(UUID sessionId) {
        TaxSession session = new TaxSession();
        session.setId(sessionId);
        session.setTaxYear(2025);
        session.setRuleVersion("rule-2025.1");
        return session;
    }

    private IncomeItem incomeItem(TaxSession session) {
        IncomeItem item = new IncomeItem();
        item.setTaxSession(session);
        item.setIncomeType(IncomeType.SALARY);
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

    private CalculationResult calculationResult(
        TaxSession session,
        String ruleVersion,
        String ruleSetId,
        String ruleSnapshotHash
    ) {
        CalculationResult result = new CalculationResult();
        result.setId(UUID.randomUUID());
        result.setTaxSession(session);
        result.setCalculationVersion(2);
        result.setRuleVersion(ruleVersion);
        result.setRuleSetId(ruleSetId);
        result.setRuleSnapshotHash(ruleSnapshotHash);
        result.setInputHash("input-hash");
        result.setTotalIncomeAmount(50_000_000L);
        result.setTotalDeductionAmount(480_000L);
        result.setTaxableIncomeAmount(49_520_000L);
        result.setCalculatedTaxAmount(2_971_200L);
        result.setTaxCreditAmount(120_000L);
        result.setFinalTaxAmount(2_851_200L);
        result.setWithholdingTaxAmount(3_000_000L);
        result.setExpectedRefundAmount(148_800L);
        result.setDecisionTraceJsonb("[]");
        result.setSummaryJsonb("{\"trace\":[\"ok\"]}");
        return result;
    }
}
