package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.RuleCategory;
import com.example.yearend.deduction.domain.RuleSetStatus;
import com.example.yearend.deduction.domain.TaxRuleSet;
import com.example.yearend.deduction.infrastructure.DeductionRuleRepository;
import com.example.yearend.deduction.infrastructure.TaxRuleSetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LawPackImportServiceTest {

    @Mock
    private TaxRuleSetRepository taxRuleSetRepository;

    @Mock
    private DeductionRuleRepository deductionRuleRepository;

    private LawPackImportService service;

    @BeforeEach
    void setUp() {
        service = new LawPackImportService(
            taxRuleSetRepository,
            deductionRuleRepository,
            new RuleVersionNormalizer(),
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("imports a new law pack as DRAFT rule set with all rules inactive")
    void importsNewLawPackAsDraft() {
        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersion(2025, "2025.01"))
            .thenReturn(Optional.empty());
        when(taxRuleSetRepository.save(any(TaxRuleSet.class))).thenAnswer(invocation -> {
            TaxRuleSet arg = invocation.getArgument(0);
            arg.setId(UUID.randomUUID());
            return arg;
        });

        AdminDtos.ImportRuleSetResponse response = service.importLawPack(validPackFile());

        assertThat(response.status()).isEqualTo(RuleSetStatus.DRAFT);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.ruleVersion()).isEqualTo("2025.01");
        assertThat(response.importedRuleCount()).isEqualTo(2);
        assertThat(response.replacedRuleCount()).isEqualTo(0);

        ArgumentCaptor<List<DeductionRule>> captor = ArgumentCaptor.forClass(List.class);
        verify(deductionRuleRepository).saveAll(captor.capture());
        List<DeductionRule> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(rule -> {
            assertThat(rule.isActive()).isFalse();
            assertThat(rule.getTaxYear()).isEqualTo(2025);
            assertThat(rule.getRuleVersion()).isEqualTo(1);
        });
        assertThat(saved.get(0).getDeductionType()).isEqualTo(DeductionType.PERSONAL_DEDUCTION);
        assertThat(saved.get(0).getRuleCategory()).isEqualTo(RuleCategory.LIMIT);
        assertThat(saved.get(1).getRuleCategory()).isEqualTo(RuleCategory.AGGREGATE_CAP);
    }

    @Test
    @DisplayName("re-imports into an existing DRAFT rule set and deletes prior rules")
    void reImportsExistingDraftRuleSet() {
        TaxRuleSet existingDraft = new TaxRuleSet();
        existingDraft.setId(UUID.randomUUID());
        existingDraft.setTaxYear(2025);
        existingDraft.setRuleVersion("2025.01");
        existingDraft.setStatus(RuleSetStatus.DRAFT);

        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersion(2025, "2025.01"))
            .thenReturn(Optional.of(existingDraft));
        when(taxRuleSetRepository.save(any(TaxRuleSet.class))).thenAnswer(inv -> inv.getArgument(0));

        DeductionRule priorRule = new DeductionRule();
        priorRule.setId(UUID.randomUUID());
        when(deductionRuleRepository.findAllByTaxRuleSetId(existingDraft.getId()))
            .thenReturn(List.of(priorRule));

        AdminDtos.ImportRuleSetResponse response = service.importLawPack(validPackFile());

        assertThat(response.ruleSetId()).isEqualTo(existingDraft.getId());
        assertThat(response.replacedRuleCount()).isEqualTo(1);
        assertThat(response.importedRuleCount()).isEqualTo(2);
        verify(deductionRuleRepository, times(1)).deleteAll(anyList());
    }

    @Test
    @DisplayName("rejects re-import when existing rule set is not DRAFT")
    void rejectsReImportWhenPublished() {
        TaxRuleSet published = new TaxRuleSet();
        published.setId(UUID.randomUUID());
        published.setTaxYear(2025);
        published.setRuleVersion("2025.01");
        published.setStatus(RuleSetStatus.PUBLISHED);

        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersion(2025, "2025.01"))
            .thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.importLawPack(validPackFile()))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RULE_SET_IMPORT_CONFLICT);

        verify(deductionRuleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("rejects malformed JSON")
    void rejectsMalformedJson() {
        MockMultipartFile broken = new MockMultipartFile(
            "file",
            "broken.json",
            "application/json",
            "{ not valid json".getBytes()
        );

        assertThatThrownBy(() -> service.importLawPack(broken))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RULE_SET_IMPORT_INVALID);
    }

    @Test
    @DisplayName("rejects empty rules[] array")
    void rejectsEmptyRules() {
        MockMultipartFile empty = pack("""
            {"taxYear":2025,"ruleVersion":"2025.01","rules":[]}
            """);

        assertThatThrownBy(() -> service.importLawPack(empty))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RULE_SET_IMPORT_INVALID);
    }

    @Test
    @DisplayName("rejects unknown deductionType in a rule")
    void rejectsUnknownDeductionType() {
        MockMultipartFile bogus = pack("""
            {
              "taxYear":2025,
              "ruleVersion":"2025.01",
              "rules":[{
                "deductionType":"NOT_A_REAL_TYPE",
                "ruleCode":"X",
                "ruleCategory":"RATE",
                "effectiveFrom":"2025-01-01",
                "parameters":{}
              }]
            }
            """);

        assertThatThrownBy(() -> service.importLawPack(bogus))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RULE_SET_IMPORT_INVALID);
    }

    private MockMultipartFile validPackFile() {
        return pack("""
            {
              "ruleSetId": "2025@2025.01-test",
              "taxYear": 2025,
              "ruleVersion": "2025.01",
              "status": "PUBLISHED",
              "rules": [
                {
                  "deductionType": "PERSONAL_DEDUCTION",
                  "subType": "BASIC",
                  "ruleCode": "PERSONAL_BASIC_DEDUCTION_AMOUNT_2025",
                  "ruleCategory": "LIMIT",
                  "parameters": {"amountPerPerson": 1500000},
                  "effectiveFrom": "2025-01-01",
                  "effectiveTo": "2025-12-31"
                },
                {
                  "deductionType": "MEDICAL_EXPENSE",
                  "ruleCode": "MEDICAL_AGGREGATE_CAP_2025",
                  "ruleCategory": "AGGREGATE_CAP",
                  "parameters": {"cap": 7000000},
                  "effectiveFrom": "2025-01-01"
                }
              ]
            }
            """);
    }

    private MockMultipartFile pack(String json) {
        return new MockMultipartFile("file", "pack.json", "application/json", json.getBytes());
    }
}
