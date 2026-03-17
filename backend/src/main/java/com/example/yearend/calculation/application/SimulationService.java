package com.example.yearend.calculation.application;

import com.example.yearend.calculation.api.SimulationDtos;
import com.example.yearend.calculation.domain.CalculationResult;
import com.example.yearend.calculation.domain.TaxCalculationCommand;
import com.example.yearend.calculation.domain.TaxCalculationOutcome;
import com.example.yearend.calculation.domain.TaxCalculationService;
import com.example.yearend.calculation.infrastructure.CalculationResultRepository;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.DeductionEngine;
import com.example.yearend.deduction.application.DeductionItemService;
import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.TaxContext;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.IncomeItemService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private final TaxSessionService taxSessionService;
    private final IncomeItemService incomeItemService;
    private final DependentService dependentService;
    private final DeductionItemService deductionItemService;
    private final DeductionEngine deductionEngine;
    private final TaxCalculationService taxCalculationService;
    private final CalculationResultRepository calculationResultRepository;
    private final DocumentChecklistService documentChecklistService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SimulationDtos.SimulationRunResponse run(String email, UUID sessionId) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        List<DeductionItem> deductionItems = deductionItemService.getEntities(email, sessionId);

        TaxContext context = buildContext(email, sessionId);
        List<DeductionDecision> decisions = deductionEngine.evaluate(context, deductionItems);
        TaxCalculationOutcome outcome = taxCalculationService.calculate(
            new TaxCalculationCommand(context.totalSalary(), context.withholdingTax(), decisions)
        );

        CalculationResult result = new CalculationResult();
        result.setTaxSession(session);
        result.setCalculationVersion(nextVersion(sessionId));
        result.setRuleVersion(session.getRuleVersion());
        result.setInputHash(hashContext(context, deductionItems));
        result.setTotalIncomeAmount(outcome.totalIncomeAmount());
        result.setTotalDeductionAmount(outcome.totalDeductionAmount());
        result.setTaxableIncomeAmount(outcome.taxableIncomeAmount());
        result.setCalculatedTaxAmount(outcome.calculatedTaxAmount());
        result.setTaxCreditAmount(outcome.taxCreditAmount());
        result.setFinalTaxAmount(outcome.finalTaxAmount());
        result.setWithholdingTaxAmount(outcome.withholdingTaxAmount());
        result.setExpectedRefundAmount(outcome.expectedRefundAmount());
        result.setDecisionTraceJsonb(toJson(decisions));
        result.setSummaryJsonb(toJson(outcome.trace()));
        calculationResultRepository.save(result);

        taxSessionService.updateStatus(session, SessionStatus.CALCULATED);
        documentChecklistService.synchronize(session, deductionItems);

        return new SimulationDtos.SimulationRunResponse(
            sessionId,
            result.getId(),
            result.getCalculationVersion(),
            outcome.totalIncomeAmount(),
            outcome.totalDeductionAmount(),
            outcome.taxableIncomeAmount(),
            outcome.calculatedTaxAmount(),
            outcome.finalTaxAmount(),
            outcome.expectedRefundAmount(),
            decisions.stream().map(this::toDecisionResponse).toList(),
            outcome.trace()
        );
    }

    @Transactional(readOnly = true)
    public SimulationDtos.CalculationResultResponse getLatestResult(String email, UUID sessionId) {
        taxSessionService.getOwnedSession(email, sessionId);
        CalculationResult result = calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CALCULATION_RESULT_NOT_FOUND));
        return toResultResponse(result);
    }

    @Transactional(readOnly = true)
    public List<SimulationDtos.RejectionReasonResponse> getRejections(String email, UUID sessionId) {
        TaxContext context = buildContext(email, sessionId);
        List<DeductionDecision> decisions = deductionEngine.evaluate(context, deductionItemService.getEntities(email, sessionId));
        return decisions.stream()
            .filter(decision -> !decision.eligible())
            .map(decision -> new SimulationDtos.RejectionReasonResponse(
                decision.deductionItemId(),
                decision.deductionType(),
                decision.reasons()
            ))
            .toList();
    }

    private TaxContext buildContext(String email, UUID sessionId) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        var incomeItems = incomeItemService.getEntities(email, sessionId);
        var dependents = dependentService.getEntities(email, sessionId);

        long totalSalary = incomeItems.stream().mapToLong(item -> item.getTaxableAmount() == null ? 0L : item.getTaxableAmount()).sum();
        long withholdingTax = incomeItems.stream().mapToLong(item -> item.getWithheldTaxAmount() == null ? 0L : item.getWithheldTaxAmount()).sum();

        return new TaxContext(
            session.getTaxYear(),
            totalSalary,
            withholdingTax,
            dependents,
            incomeItems
        );
    }

    private int nextVersion(UUID sessionId) {
        return calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId)
            .map(existing -> existing.getCalculationVersion() + 1)
            .orElse(1);
    }

    private String hashContext(TaxContext context, List<DeductionItem> deductionItems) {
        String raw = context.taxYear() + "|" + context.totalSalary() + "|" + context.withholdingTax() + "|" +
            deductionItems.stream()
                .map(item -> item.getDeductionType() + ":" + item.getAmount() + ":" + item.getSubType())
                .sorted()
                .reduce("", String::concat);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize calculation payload.", exception);
        }
    }

    private SimulationDtos.DeductionDecisionResponse toDecisionResponse(DeductionDecision decision) {
        return new SimulationDtos.DeductionDecisionResponse(
            decision.deductionItemId(),
            decision.deductionType(),
            decision.eligible(),
            decision.requestedAmount(),
            decision.eligibleAmount(),
            decision.appliedAmount(),
            decision.reasons()
        );
    }

    private SimulationDtos.CalculationResultResponse toResultResponse(CalculationResult result) {
        return new SimulationDtos.CalculationResultResponse(
            result.getId(),
            result.getCalculationVersion(),
            result.getRuleVersion(),
            result.getTotalIncomeAmount(),
            result.getTotalDeductionAmount(),
            result.getTaxableIncomeAmount(),
            result.getCalculatedTaxAmount(),
            result.getTaxCreditAmount(),
            result.getFinalTaxAmount(),
            result.getWithholdingTaxAmount(),
            result.getExpectedRefundAmount(),
            result.getDecisionTraceJsonb(),
            result.getSummaryJsonb()
        );
    }
}
