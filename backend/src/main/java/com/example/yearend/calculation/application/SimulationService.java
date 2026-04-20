package com.example.yearend.calculation.application;

import com.example.yearend.calculation.api.SimulationDtos;
import com.example.yearend.calculation.domain.CalculationResult;
import com.example.yearend.calculation.domain.EarnedIncomeTaxCreditCalculator;
import com.example.yearend.calculation.domain.IncomeTaxRateTableCalculator;
import com.example.yearend.calculation.domain.PensionInsurancePremiumCalculator;
import com.example.yearend.calculation.domain.SocialInsurancePremiumCalculator;
import com.example.yearend.calculation.domain.CreditCardDeductionCalculator;
import com.example.yearend.calculation.domain.DonationTaxCreditCalculator;
import com.example.yearend.calculation.domain.ChildTaxCreditCalculator;
import com.example.yearend.calculation.domain.PensionAccountTaxCreditCalculator;
import com.example.yearend.calculation.domain.MonthlyRentTaxCreditCalculator;
import com.example.yearend.calculation.domain.HousingLoanDeductionCalculator;
import com.example.yearend.calculation.domain.LongTermMortgageDeductionCalculator;
import com.example.yearend.calculation.domain.HousingSavingsDeductionCalculator;
import com.example.yearend.calculation.domain.LongTermCollectiveInvestmentDeductionCalculator;
import com.example.yearend.calculation.domain.YouthLongTermCollectiveInvestmentDeductionCalculator;
import com.example.yearend.calculation.domain.SmeMutualAidDeductionCalculator;
import com.example.yearend.calculation.domain.VentureInvestmentDeductionCalculator;
import com.example.yearend.calculation.domain.EmployeeStockOwnershipDeductionCalculator;
import com.example.yearend.calculation.domain.ForeignTaxCreditCalculator;
import com.example.yearend.calculation.domain.TaxCalculationCommand;
import com.example.yearend.calculation.domain.TaxCalculationOutcome;
import com.example.yearend.calculation.domain.TaxCalculationService;
import com.example.yearend.calculation.infrastructure.CalculationResultRepository;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.DeductionEngine;
import com.example.yearend.deduction.application.DeductionItemService;
import com.example.yearend.deduction.application.DeductionItemReviewPolicy;
import com.example.yearend.deduction.application.RuleSetResolver;
import com.example.yearend.deduction.domain.DeductionDecision;
import com.example.yearend.deduction.domain.DeductionItem;
import com.example.yearend.deduction.domain.RuleSetSnapshot;
import com.example.yearend.deduction.domain.TaxContext;
import com.example.yearend.document.application.DocumentChecklistService;
import com.example.yearend.taxsession.application.DependentService;
import com.example.yearend.taxsession.application.IncomeItemService;
import com.example.yearend.taxsession.application.TaxSessionService;
import com.example.yearend.taxsession.domain.IncomeItem;
import com.example.yearend.taxsession.domain.IncomeType;
import com.example.yearend.taxsession.domain.SessionStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES_TYPE = new TypeReference<>() {
    };

    private final TaxSessionService taxSessionService;
    private final IncomeItemService incomeItemService;
    private final DependentService dependentService;
    private final DeductionItemService deductionItemService;
    private final RuleSetResolver ruleSetResolver;
    private final DeductionItemReviewPolicy deductionItemReviewPolicy;
    private final DeductionEngine deductionEngine;
    private final TaxCalculationService taxCalculationService;
    private final CalculationResultRepository calculationResultRepository;
    private final DocumentChecklistService documentChecklistService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SimulationDtos.SimulationRunResponse run(String email, UUID sessionId) {
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        List<DeductionItem> deductionItems = deductionItemService.getCalculationEligibleEntities(email, sessionId);
        RuleSetSnapshot ruleSetSnapshot = resolveRuleSet(session);

        TaxContext context = buildContext(email, session, ruleSetSnapshot);
        IncomeCalculationSummary incomeSummary = summarizeIncomeItems(context.incomeItems());
        long publicPensionContributionAmount = summarizePublicPensionContributions(context.incomeItems());
        long healthInsurancePremiumAmount = summarizeHealthInsurancePremiums(context.incomeItems());
        long employmentInsurancePremiumAmount = summarizeEmploymentInsurancePremiums(context.incomeItems());
        CreditCardUsageSummary creditCardSummary = summarizeCreditCardItems(deductionItems);
        DonationSummary donationSummary = summarizeDonationItems(deductionItems);
        PensionAccountSummary pensionAccountSummary = summarizePensionAccountItems(deductionItems);
        long annualRentAmount = summarizeMonthlyRentItems(deductionItems);
        HousingLoanRepaymentSummary housingLoanSummary = summarizeHousingLoanItems(deductionItems);
        LongTermMortgageSummary longTermMortgageSummary = summarizeLongTermMortgageItems(deductionItems);
        HousingSavingsSummary housingSavingsSummary = summarizeHousingSavingsItems(deductionItems);
        LongTermCollectiveInvestmentSummary longTermCollectiveInvestmentSummary = summarizeLongTermCollectiveInvestmentItems(deductionItems);
        YouthLongTermCollectiveInvestmentSummary youthLongTermCollectiveInvestmentSummary = summarizeYouthLongTermCollectiveInvestmentItems(deductionItems);
        SmeMutualAidSummary smeMutualAidSummary = summarizeSmeMutualAidItems(deductionItems);
        VentureInvestmentSummary ventureInvestmentSummary = summarizeVentureInvestmentItems(deductionItems);
        EmployeeStockOwnershipSummary employeeStockOwnershipSummary = summarizeEmployeeStockOwnershipItems(deductionItems);
        ForeignTaxCreditSummary foreignTaxCreditSummary = summarizeForeignTaxCreditItems(deductionItems);
        List<DeductionDecision> decisions = deductionEngine.evaluate(context, deductionItems);
        TaxCalculationOutcome outcome = taxCalculationService.calculate(
            new TaxCalculationCommand(
                session.getTaxYear(),
                incomeSummary.totalGrossSalaryAmount(),
                incomeSummary.totalNonTaxableIncomeAmount(),
                incomeSummary.taxableSalaryAmount(),
                incomeSummary.otherTaxableIncomeAmount(),
                incomeSummary.withholdingTaxAmount(),
                publicPensionContributionAmount,
                healthInsurancePremiumAmount,
                employmentInsurancePremiumAmount,
                creditCardSummary.creditCardAmount(),
                creditCardSummary.debitCardAmount(),
                creditCardSummary.cashReceiptAmount(),
                creditCardSummary.zeroPayAmount(),
                donationSummary.politicalAmount(),
                donationSummary.legalAmount(),
                donationSummary.employeeStockAmount(),
                donationSummary.designatedAmount(),
                donationSummary.designatedReligiousAmount(),
                pensionAccountSummary.pensionSavingsAmount(),
                pensionAccountSummary.irpAmount(),
                pensionAccountSummary.isaMaturityTransferAmount(),
                annualRentAmount,
                housingLoanSummary.bankRepaymentAmount(),
                housingLoanSummary.individualRepaymentAmount(),
                longTermMortgageSummary.interestAmount(),
                longTermMortgageSummary.repaymentType(),
                housingSavingsSummary.contributionAmount(),
                longTermCollectiveInvestmentSummary.contributionAmount(),
                youthLongTermCollectiveInvestmentSummary.contributionAmount(),
                smeMutualAidSummary.contributionAmount(),
                smeMutualAidSummary.incomeBasisAmount(),
                ventureInvestmentSummary.directInvestmentAmount(),
                ventureInvestmentSummary.fundInvestmentAmount(),
                employeeStockOwnershipSummary.contributionAmount(),
                employeeStockOwnershipSummary.isVentureEmployer(),
                foreignTaxCreditSummary.foreignTaxPaidAmount(),
                foreignTaxCreditSummary.foreignSourceIncomeAmount(),
                context.dependents(),
                context.basicInfoAttributes(),
                decisions,
                ruleSetSnapshot
            )
        );

        CalculationResult result = new CalculationResult();
        result.setTaxSession(session);
        result.setCalculationVersion(nextVersion(sessionId));
        result.setRuleVersion(ruleSetSnapshot.ruleVersion());
        result.setRuleSetId(ruleSetSnapshot.ruleSetId());
        result.setRuleSnapshotHash(ruleSetSnapshot.ruleSnapshotHash());
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
        result.setSummaryJsonb(toJson(toSummaryPayload(outcome)));
        calculationResultRepository.save(result);

        taxSessionService.updateStatus(session, SessionStatus.CALCULATED);
        documentChecklistService.synchronize(
            session,
            deductionItems.stream().filter(deductionItemReviewPolicy::isIncludedInDocumentChecklist).toList()
        );

        return new SimulationDtos.SimulationRunResponse(
            sessionId,
            result.getId(),
            result.getCalculationVersion(),
            outcome.totalIncomeAmount(),
            outcome.totalGrossSalaryAmount(),
            outcome.totalNonTaxableIncomeAmount(),
            outcome.taxableSalaryAmount(),
            outcome.otherTaxableIncomeAmount(),
            outcome.earnedIncomeDeductionAmount(),
            outcome.earnedIncomeAmount(),
            outcome.personalDeductionAmount(),
            outcome.pensionInsurancePremiumDeductionAmount(),
            outcome.socialInsurancePremiumDeductionAmount(),
            outcome.creditCardDeductionAmount(),
            outcome.housingLoanDeductionAmount(),
            outcome.longTermMortgageDeductionAmount(),
            outcome.housingSavingsDeductionAmount(),
            outcome.longTermCollectiveInvestmentDeductionAmount(),
            outcome.youthLongTermCollectiveInvestmentDeductionAmount(),
            outcome.smeMutualAidDeductionAmount(),
            outcome.ventureInvestmentDeductionAmount(),
            outcome.employeeStockOwnershipDeductionAmount(),
            outcome.totalDeductionAmount(),
            outcome.taxableIncomeAmount(),
            outcome.calculatedTaxAmount(),
            outcome.earnedIncomeTaxCreditAmount(),
            outcome.donationTaxCreditAmount(),
            outcome.childTaxCreditAmount(),
            outcome.pensionAccountTaxCreditAmount(),
            outcome.monthlyRentTaxCreditAmount(),
            outcome.foreignTaxCreditAmount(),
            outcome.otherTaxCreditAmount(),
            outcome.taxCreditAmount(),
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
        TaxSession session = taxSessionService.getOwnedSession(email, sessionId);
        TaxContext context = buildContext(email, session, resolveRuleSet(session));
        List<DeductionDecision> decisions = deductionEngine.evaluate(
            context,
            deductionItemService.getCalculationEligibleEntities(email, sessionId)
        );
        return decisions.stream()
            .filter(decision -> !decision.eligible())
            .map(decision -> new SimulationDtos.RejectionReasonResponse(
                decision.deductionItemId(),
                decision.deductionType(),
                decision.reasons()
            ))
            .toList();
    }

    private TaxContext buildContext(String email, TaxSession session, RuleSetSnapshot ruleSetSnapshot) {
        var incomeItems = incomeItemService.getEntities(email, session.getId());
        var dependents = dependentService.getEntities(email, session.getId());
        IncomeCalculationSummary incomeSummary = summarizeIncomeItems(incomeItems);

        return new TaxContext(
            session.getTaxYear(),
            incomeSummary.taxableSalaryAmount(),
            incomeSummary.withholdingTaxAmount(),
            dependents,
            incomeItems,
            readBasicInfoAttributes(session),
            ruleSetSnapshot
        );
    }

    private RuleSetSnapshot resolveRuleSet(TaxSession session) {
        return ruleSetResolver.resolve(
            session.getTaxYear(),
            session.getRuleVersion(),
            LocalDate.of(session.getTaxYear(), 12, 31)
        );
    }

    private int nextVersion(UUID sessionId) {
        return calculationResultRepository.findFirstByTaxSessionIdOrderByCalculationVersionDesc(sessionId)
            .map(existing -> existing.getCalculationVersion() + 1)
            .orElse(1);
    }

    private String hashContext(TaxContext context, List<DeductionItem> deductionItems) {
        String raw = context.taxYear() + "|" + context.totalSalary() + "|" + context.withholdingTax() + "|" +
            toJson(context.basicInfoAttributes()) +
            context.dependents().stream()
                .map(dependent -> dependent.getRelationType() + ":" + dependent.getBirthDate() + ":" +
                    nullToZero(dependent.getAnnualIncomeAmount()) + ":" + dependent.getResidentType() + ":" +
                    dependent.isLivesTogether() + ":" + dependent.isDisabled() + ":" +
                    dependent.isBasicDeductionTarget())
                .sorted()
                .reduce("", String::concat) +
            context.incomeItems().stream()
                .map(item -> item.getIncomeType() + ":" + nullToZero(item.getGrossAmount()) + ":" +
                    nullToZero(item.getTaxableAmount()) + ":" + nullToZero(item.getNonTaxableAmount()) + ":" +
                    nullToZero(item.getWithheldTaxAmount()))
                .sorted()
                .reduce("", String::concat) +
            deductionItems.stream()
                .map(item -> item.getDeductionType() + ":" + item.getAmount() + ":" + item.getSubType())
                .sorted()
                .reduce("", String::concat);
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private IncomeCalculationSummary summarizeIncomeItems(List<IncomeItem> incomeItems) {
        long totalGrossSalaryAmount = incomeItems.stream()
            .filter(this::isEmploymentIncome)
            .mapToLong(item -> nullToZero(item.getGrossAmount()))
            .sum();
        long totalNonTaxableIncomeAmount = incomeItems.stream()
            .filter(this::isEmploymentIncome)
            .mapToLong(item -> nullToZero(item.getNonTaxableAmount()))
            .sum();
        long taxableSalaryAmount = incomeItems.stream()
            .filter(this::isEmploymentIncome)
            .mapToLong(this::resolveTaxableIncomeAmount)
            .sum();
        long otherTaxableIncomeAmount = incomeItems.stream()
            .filter(item -> !isEmploymentIncome(item))
            .mapToLong(this::resolveTaxableIncomeAmount)
            .sum();
        long withholdingTaxAmount = incomeItems.stream()
            .mapToLong(item -> nullToZero(item.getWithheldTaxAmount()))
            .sum();

        return new IncomeCalculationSummary(
            totalGrossSalaryAmount,
            totalNonTaxableIncomeAmount,
            taxableSalaryAmount,
            otherTaxableIncomeAmount,
            withholdingTaxAmount
        );
    }

    private long summarizePublicPensionContributions(List<IncomeItem> incomeItems) {
        return incomeItems.stream()
            .mapToLong(this::readPublicPensionContributionAmount)
            .filter(amount -> amount > 0L)
            .sum();
    }

    private long readPublicPensionContributionAmount(IncomeItem item) {
        return readAttributeLong(item, "employeePublicPensionContributionAmount");
    }

    private long summarizeHealthInsurancePremiums(List<IncomeItem> incomeItems) {
        return incomeItems.stream()
            .mapToLong(item -> readAttributeLong(item, "employeeHealthInsurancePremiumAmount"))
            .filter(amount -> amount > 0L)
            .sum();
    }

    private long summarizeEmploymentInsurancePremiums(List<IncomeItem> incomeItems) {
        return incomeItems.stream()
            .mapToLong(item -> readAttributeLong(item, "employeeEmploymentInsurancePremiumAmount"))
            .filter(amount -> amount > 0L)
            .sum();
    }

    private CreditCardUsageSummary summarizeCreditCardItems(List<DeductionItem> deductionItems) {
        long creditCard = sumCreditCardSubType(deductionItems, "CREDIT_CARD");
        long debitCard  = sumCreditCardSubType(deductionItems, "DEBIT_CARD");
        long cashReceipt = sumCreditCardSubType(deductionItems, "CASH_RECEIPT");
        long zeroPay    = sumCreditCardSubType(deductionItems, "ZERO_PAY");
        return new CreditCardUsageSummary(creditCard, debitCard, cashReceipt, zeroPay);
    }

    private long sumCreditCardSubType(List<DeductionItem> deductionItems, String subType) {
        return deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.CREDIT_CARD
                && subType.equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
    }

    private DonationSummary summarizeDonationItems(List<DeductionItem> deductionItems) {
        long political = sumDonationSubType(deductionItems, "POLITICAL");
        long legal = sumDonationSubType(deductionItems, "LEGAL");
        long employeeStock = sumDonationSubType(deductionItems, "EMPLOYEE_STOCK");
        long designated = sumDonationSubType(deductionItems, "DESIGNATED");
        long designatedReligious = sumDonationSubType(deductionItems, "DESIGNATED_RELIGIOUS");
        return new DonationSummary(political, legal, employeeStock, designated, designatedReligious);
    }

    private long summarizeMonthlyRentItems(List<DeductionItem> deductionItems) {
        return deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.MONTHLY_RENT)
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
    }

    private LongTermMortgageSummary summarizeLongTermMortgageItems(List<DeductionItem> deductionItems) {
        long interestAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.LONG_TERM_MORTGAGE)
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        String repaymentType = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.LONG_TERM_MORTGAGE
                && item.getSubType() != null)
            .map(item -> item.getSubType())
            .findFirst()
            .orElse("NONE");
        return new LongTermMortgageSummary(interestAmount, repaymentType);
    }

    private HousingSavingsSummary summarizeHousingSavingsItems(List<DeductionItem> deductionItems) {
        long contributionAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.HOUSING_SAVINGS)
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new HousingSavingsSummary(contributionAmount);
    }

    private LongTermCollectiveInvestmentSummary summarizeLongTermCollectiveInvestmentItems(List<DeductionItem> deductionItems) {
        long contributionAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.LONG_TERM_COLLECTIVE_INVESTMENT)
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new LongTermCollectiveInvestmentSummary(contributionAmount);
    }

    private YouthLongTermCollectiveInvestmentSummary summarizeYouthLongTermCollectiveInvestmentItems(List<DeductionItem> deductionItems) {
        long contributionAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.YOUTH_LONG_TERM_COLLECTIVE_INVESTMENT)
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new YouthLongTermCollectiveInvestmentSummary(contributionAmount);
    }

    private SmeMutualAidSummary summarizeSmeMutualAidItems(List<DeductionItem> deductionItems) {
        long contributionAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.SME_MUTUAL_AID
                && "CONTRIBUTION".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        long incomeBasisAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.SME_MUTUAL_AID
                && "INCOME_BASIS".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new SmeMutualAidSummary(contributionAmount, incomeBasisAmount);
    }

    private VentureInvestmentSummary summarizeVentureInvestmentItems(List<DeductionItem> deductionItems) {
        long directInvestmentAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.VENTURE_INVESTMENT
                && "DIRECT".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        long fundInvestmentAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.VENTURE_INVESTMENT
                && "FUND".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new VentureInvestmentSummary(directInvestmentAmount, fundInvestmentAmount);
    }

    private EmployeeStockOwnershipSummary summarizeEmployeeStockOwnershipItems(List<DeductionItem> deductionItems) {
        long contributionAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.EMPLOYEE_STOCK_OWNERSHIP
                && "CONTRIBUTION".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        boolean isVentureEmployer = deductionItems.stream()
            .anyMatch(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.EMPLOYEE_STOCK_OWNERSHIP
                && "VENTURE_EMPLOYER".equals(item.getSubType())
                && item.getAmount() != null && item.getAmount() > 0L);
        return new EmployeeStockOwnershipSummary(contributionAmount, isVentureEmployer);
    }

    private ForeignTaxCreditSummary summarizeForeignTaxCreditItems(List<DeductionItem> deductionItems) {
        long foreignTaxPaidAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.FOREIGN_TAX_CREDIT
                && "FOREIGN_TAX_PAID".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        long foreignSourceIncomeAmount = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.FOREIGN_TAX_CREDIT
                && "FOREIGN_SOURCE_INCOME".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new ForeignTaxCreditSummary(foreignTaxPaidAmount, foreignSourceIncomeAmount);
    }

    private HousingLoanRepaymentSummary summarizeHousingLoanItems(List<DeductionItem> deductionItems) {
        long bankRepayment = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.HOUSING_LOAN
                && "BANK".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        long individualRepayment = deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.HOUSING_LOAN
                && "INDIVIDUAL".equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
        return new HousingLoanRepaymentSummary(bankRepayment, individualRepayment);
    }

    private PensionAccountSummary summarizePensionAccountItems(List<DeductionItem> deductionItems) {
        long pensionSavings = sumPensionAccountSubType(deductionItems, "PENSION_SAVINGS");
        long irp = sumPensionAccountSubType(deductionItems, "IRP");
        long isaMaturity = sumPensionAccountSubType(deductionItems, "ISA_MATURITY_TRANSFER");
        return new PensionAccountSummary(pensionSavings, irp, isaMaturity);
    }

    private long sumPensionAccountSubType(List<DeductionItem> deductionItems, String subType) {
        return deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.PENSION_ACCOUNT
                && subType.equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
    }

    private long sumDonationSubType(List<DeductionItem> deductionItems, String subType) {
        return deductionItems.stream()
            .filter(item -> item.getDeductionType() == com.example.yearend.deduction.domain.DeductionType.DONATION
                && subType.equals(item.getSubType()))
            .mapToLong(item -> item.getAmount() == null ? 0L : item.getAmount())
            .sum();
    }

    private long readAttributeLong(IncomeItem item, String key) {
        try {
            Map<String, Object> attributes = objectMapper.readValue(
                item.getAttributesJsonb() == null ? "{}" : item.getAttributesJsonb(),
                ATTRIBUTES_TYPE
            );
            Object rawValue = attributes.get(key);
            if (rawValue == null) {
                return 0L;
            }
            if (rawValue instanceof Number number) {
                return number.longValue();
            }
            String text = rawValue.toString().trim();
            if (text.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(text);
        } catch (JsonProcessingException | NumberFormatException exception) {
            return 0L;
        }
    }

    private boolean isEmploymentIncome(IncomeItem item) {
        String entryKind = readEntryKind(item);
        if ("OTHER_INCOME".equals(entryKind)) {
            return false;
        }
        return item.getIncomeType() == IncomeType.SALARY || item.getIncomeType() == IncomeType.BONUS;
    }

    private long resolveTaxableIncomeAmount(IncomeItem item) {
        long grossAmount = nullToZero(item.getGrossAmount());
        long nonTaxableAmount = nullToZero(item.getNonTaxableAmount());
        if (grossAmount > 0L || nonTaxableAmount > 0L) {
            return Math.max(0L, grossAmount - nonTaxableAmount);
        }
        return nullToZero(item.getTaxableAmount());
    }

    private String readEntryKind(IncomeItem item) {
        try {
            Map<String, Object> attributes = objectMapper.readValue(
                item.getAttributesJsonb() == null ? "{}" : item.getAttributesJsonb(),
                ATTRIBUTES_TYPE
            );
            Object entryKind = attributes.get("entryKind");
            return entryKind == null ? "" : entryKind.toString();
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private CalculationSummaryPayload toSummaryPayload(TaxCalculationOutcome outcome) {
        return new CalculationSummaryPayload(
            outcome.totalIncomeAmount(),
            outcome.totalGrossSalaryAmount(),
            outcome.totalNonTaxableIncomeAmount(),
            outcome.taxableSalaryAmount(),
            outcome.otherTaxableIncomeAmount(),
            outcome.earnedIncomeDeductionAmount(),
            outcome.earnedIncomeAmount(),
            outcome.personalDeductionAmount(),
            outcome.pensionInsurancePremiumDeductionAmount(),
            outcome.socialInsurancePremiumDeductionAmount(),
            outcome.creditCardDeductionAmount(),
            outcome.housingLoanDeductionAmount(),
            outcome.longTermMortgageDeductionAmount(),
            outcome.housingSavingsDeductionAmount(),
            outcome.longTermCollectiveInvestmentDeductionAmount(),
            outcome.youthLongTermCollectiveInvestmentDeductionAmount(),
            outcome.smeMutualAidDeductionAmount(),
            outcome.ventureInvestmentDeductionAmount(),
            outcome.employeeStockOwnershipDeductionAmount(),
            outcome.totalDeductionAmount(),
            outcome.taxableIncomeAmount(),
            outcome.calculatedTaxAmount(),
            outcome.earnedIncomeTaxCreditAmount(),
            outcome.donationTaxCreditAmount(),
            outcome.childTaxCreditAmount(),
            outcome.pensionAccountTaxCreditAmount(),
            outcome.monthlyRentTaxCreditAmount(),
            outcome.foreignTaxCreditAmount(),
            outcome.otherTaxCreditAmount(),
            outcome.taxCreditAmount(),
            outcome.finalTaxAmount(),
            outcome.withholdingTaxAmount(),
            outcome.expectedRefundAmount(),
            List.of(
                "EMPLOYMENT_TAXABLE_SALARY_FORMULA",
                "EARNED_INCOME_DEDUCTION_BRACKETS",
                "EARNED_INCOME_DEDUCTION_MAX_LIMIT",
                "EARNED_INCOME_AMOUNT_FORMULA",
                "PERSONAL_BASIC_DEDUCTION_AMOUNT_2025",
                "PERSONAL_BASIC_ELIGIBILITY_2025",
                "PERSONAL_ADDITIONAL_SENIOR_2025",
                "PERSONAL_ADDITIONAL_DISABLED_2025",
                "PERSONAL_ADDITIONAL_WOMAN_2025",
                "PERSONAL_ADDITIONAL_SINGLE_PARENT_2025",
                "PERSONAL_DEDUCTION_AGGREGATION_FORMULA_2025",
                PensionInsurancePremiumCalculator.ELIGIBILITY_RULE_CODE,
                PensionInsurancePremiumCalculator.PAID_AMOUNT_RULE_CODE,
                PensionInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE,
                PensionInsurancePremiumCalculator.TRACE_RULE_CODE,
                SocialInsurancePremiumCalculator.HEALTH_ELIGIBILITY_RULE_CODE,
                SocialInsurancePremiumCalculator.EMPLOYMENT_ELIGIBILITY_RULE_CODE,
                SocialInsurancePremiumCalculator.AGGREGATE_CAP_RULE_CODE,
                SocialInsurancePremiumCalculator.TRACE_RULE_CODE,
                CreditCardDeductionCalculator.MINIMUM_USAGE_THRESHOLD_RULE_CODE,
                CreditCardDeductionCalculator.RATE_CREDIT_RULE_CODE,
                CreditCardDeductionCalculator.RATE_DEBIT_CASH_ZEROPAY_RULE_CODE,
                CreditCardDeductionCalculator.BASIC_LIMIT_RULE_CODE,
                CreditCardDeductionCalculator.TRACE_RULE_CODE,
                "COMPREHENSIVE_INCOME_AMOUNT_FORMULA_2025",
                IncomeTaxRateTableCalculator.TAX_BASE_FORMULA_RULE_CODE,
                IncomeTaxRateTableCalculator.BRACKETS_RULE_CODE,
                IncomeTaxRateTableCalculator.CALCULATED_TAX_FORMULA_RULE_CODE,
                EarnedIncomeTaxCreditCalculator.BASE_FORMULA_RULE_CODE,
                EarnedIncomeTaxCreditCalculator.LIMIT_BY_GROSS_SALARY_RULE_CODE,
                EarnedIncomeTaxCreditCalculator.FINAL_FORMULA_RULE_CODE,
                DonationTaxCreditCalculator.POLITICAL_BRACKET_RULE_CODE,
                DonationTaxCreditCalculator.LEGAL_LIMIT_RATE_RULE_CODE,
                DonationTaxCreditCalculator.DESIGNATED_RELIGIOUS_RULE_CODE,
                DonationTaxCreditCalculator.DESIGNATED_RULE_CODE,
                DonationTaxCreditCalculator.EMPLOYEE_STOCK_RULE_CODE,
                DonationTaxCreditCalculator.AGGREGATE_FORMULA_RULE_CODE,
                DonationTaxCreditCalculator.TRACE_RULE_CODE,
                ChildTaxCreditCalculator.AMOUNT_RULE_CODE,
                ChildTaxCreditCalculator.BIRTH_ADOPTION_RULE_CODE,
                ChildTaxCreditCalculator.TRACE_RULE_CODE,
                PensionAccountTaxCreditCalculator.LIMIT_RULE_CODE,
                PensionAccountTaxCreditCalculator.RATE_RULE_CODE,
                PensionAccountTaxCreditCalculator.TRACE_RULE_CODE,
                MonthlyRentTaxCreditCalculator.LIMIT_RULE_CODE,
                MonthlyRentTaxCreditCalculator.RATE_RULE_CODE,
                MonthlyRentTaxCreditCalculator.TRACE_RULE_CODE,
                HousingLoanDeductionCalculator.LIMIT_RULE_CODE,
                HousingLoanDeductionCalculator.RATE_RULE_CODE,
                HousingLoanDeductionCalculator.TRACE_RULE_CODE,
                LongTermMortgageDeductionCalculator.LIMIT_RULE_CODE,
                LongTermMortgageDeductionCalculator.TRACE_RULE_CODE,
                HousingSavingsDeductionCalculator.LIMIT_RULE_CODE,
                HousingSavingsDeductionCalculator.RATE_RULE_CODE,
                HousingSavingsDeductionCalculator.TRACE_RULE_CODE,
                LongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE,
                LongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE,
                LongTermCollectiveInvestmentDeductionCalculator.TRACE_RULE_CODE,
                YouthLongTermCollectiveInvestmentDeductionCalculator.LIMIT_RULE_CODE,
                YouthLongTermCollectiveInvestmentDeductionCalculator.RATE_RULE_CODE,
                YouthLongTermCollectiveInvestmentDeductionCalculator.TRACE_RULE_CODE,
                SmeMutualAidDeductionCalculator.TIERED_LIMIT_RULE_CODE,
                SmeMutualAidDeductionCalculator.RATE_RULE_CODE,
                SmeMutualAidDeductionCalculator.TRACE_RULE_CODE,
                VentureInvestmentDeductionCalculator.RATE_DIRECT_TIERED_RULE_CODE,
                VentureInvestmentDeductionCalculator.RATE_FUND_RULE_CODE,
                VentureInvestmentDeductionCalculator.INCOME_LIMIT_RULE_CODE,
                VentureInvestmentDeductionCalculator.TRACE_RULE_CODE,
                EmployeeStockOwnershipDeductionCalculator.RATE_RULE_CODE,
                EmployeeStockOwnershipDeductionCalculator.LIMIT_RULE_CODE,
                EmployeeStockOwnershipDeductionCalculator.TRACE_RULE_CODE,
                ForeignTaxCreditCalculator.RATE_RULE_CODE,
                ForeignTaxCreditCalculator.LIMIT_FORMULA_RULE_CODE,
                ForeignTaxCreditCalculator.TRACE_RULE_CODE
            ),
            outcome.trace()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize calculation payload.", exception);
        }
    }

    private Map<String, Object> readBasicInfoAttributes(TaxSession session) {
        try {
            return objectMapper.readValue(
                session.getBasicInfoJsonb() == null ? "{}" : session.getBasicInfoJsonb(),
                ATTRIBUTES_TYPE
            );
        } catch (JsonProcessingException exception) {
            return Map.of();
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
            result.getRuleSetId(),
            result.getRuleSnapshotHash(),
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

    private record CreditCardUsageSummary(
        long creditCardAmount,
        long debitCardAmount,
        long cashReceiptAmount,
        long zeroPayAmount
    ) {
    }

    private record PensionAccountSummary(
        long pensionSavingsAmount,
        long irpAmount,
        long isaMaturityTransferAmount
    ) {
    }

    private record LongTermMortgageSummary(
        long interestAmount,
        String repaymentType
    ) {
    }

    private record HousingSavingsSummary(
        long contributionAmount
    ) {
    }

    private record LongTermCollectiveInvestmentSummary(
        long contributionAmount
    ) {
    }

    private record YouthLongTermCollectiveInvestmentSummary(
        long contributionAmount
    ) {
    }

    private record SmeMutualAidSummary(
        long contributionAmount,
        long incomeBasisAmount
    ) {
    }

    private record VentureInvestmentSummary(
        long directInvestmentAmount,
        long fundInvestmentAmount
    ) {
    }

    private record EmployeeStockOwnershipSummary(
        long contributionAmount,
        boolean isVentureEmployer
    ) {
    }

    private record ForeignTaxCreditSummary(
        long foreignTaxPaidAmount,
        long foreignSourceIncomeAmount
    ) {
    }

    private record HousingLoanRepaymentSummary(
        long bankRepaymentAmount,
        long individualRepaymentAmount
    ) {
    }

    private record DonationSummary(
        long politicalAmount,
        long legalAmount,
        long employeeStockAmount,
        long designatedAmount,
        long designatedReligiousAmount
    ) {
    }

    private record IncomeCalculationSummary(
        long totalGrossSalaryAmount,
        long totalNonTaxableIncomeAmount,
        long taxableSalaryAmount,
        long otherTaxableIncomeAmount,
        long withholdingTaxAmount
    ) {
    }

    private record CalculationSummaryPayload(
        long totalIncomeAmount,
        long totalGrossSalaryAmount,
        long totalNonTaxableIncomeAmount,
        long taxableSalaryAmount,
        long otherTaxableIncomeAmount,
        long earnedIncomeDeductionAmount,
        long earnedIncomeAmount,
        long personalDeductionAmount,
        long pensionInsurancePremiumDeductionAmount,
        long socialInsurancePremiumDeductionAmount,
        long creditCardDeductionAmount,
        long housingLoanDeductionAmount,
        long longTermMortgageDeductionAmount,
        long housingSavingsDeductionAmount,
        long longTermCollectiveInvestmentDeductionAmount,
        long youthLongTermCollectiveInvestmentDeductionAmount,
        long smeMutualAidDeductionAmount,
        long ventureInvestmentDeductionAmount,
        long employeeStockOwnershipDeductionAmount,
        long totalDeductionAmount,
        long taxableIncomeAmount,
        long calculatedTaxAmount,
        long earnedIncomeTaxCreditAmount,
        long donationTaxCreditAmount,
        long childTaxCreditAmount,
        long pensionAccountTaxCreditAmount,
        long monthlyRentTaxCreditAmount,
        long foreignTaxCreditAmount,
        long otherTaxCreditAmount,
        long taxCreditAmount,
        long finalTaxAmount,
        long withholdingTaxAmount,
        long expectedRefundAmount,
        List<String> ruleCodes,
        List<String> trace
    ) {
    }
}
