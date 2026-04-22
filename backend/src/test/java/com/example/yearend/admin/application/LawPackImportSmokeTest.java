package com.example.yearend.admin.application;

import com.example.yearend.admin.api.AdminDtos;
import com.example.yearend.deduction.application.RuleVersionNormalizer;
import com.example.yearend.deduction.domain.DeductionRule;
import com.example.yearend.deduction.domain.DeductionType;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end smoke test: loads the real canonical 2025.01 law pack from
 * plugins/year-end-harness/law-packs/ and verifies the importer can parse
 * every single rule without any enum or schema mismatch.
 *
 * Uses mocked repositories (no DB) — the purpose is to catch schema drift
 * between harness output and backend enums, not to test persistence.
 */
@ExtendWith(MockitoExtension.class)
class LawPackImportSmokeTest {

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
    @DisplayName("smokes the real 2025.01 law pack: every rule parses into a known enum")
    void importsReal202501LawPack() throws Exception {
        Path lawPack = locateLawPack();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "normalized-rule-pack.json",
            "application/json",
            Files.readAllBytes(lawPack)
        );

        when(taxRuleSetRepository.findFirstByTaxYearAndRuleVersion(any(), any()))
            .thenReturn(Optional.empty());
        when(taxRuleSetRepository.save(any(TaxRuleSet.class))).thenAnswer(invocation -> {
            TaxRuleSet saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AdminDtos.ImportRuleSetResponse response = service.importLawPack(file);

        assertThat(response.status()).isEqualTo(RuleSetStatus.DRAFT);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.ruleVersion()).isEqualTo("2025.01");
        assertThat(response.replacedRuleCount()).isEqualTo(0);
        assertThat(response.importedRuleCount())
            .as("all rules in the canonical pack should import")
            .isGreaterThanOrEqualTo(90);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DeductionRule>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(deductionRuleRepository).saveAll(captor.capture());
        List<DeductionRule> saved = captor.getValue();

        assertThat(saved)
            .as("every rule must resolve to a known DeductionType, RuleCategory, and have non-null effectiveFrom")
            .allSatisfy(rule -> {
                assertThat(rule.getDeductionType()).isNotNull();
                assertThat(rule.getRuleCategory()).isNotNull();
                assertThat(rule.getRuleCode()).isNotBlank();
                assertThat(rule.getEffectiveFrom()).isNotNull();
                assertThat(rule.isActive()).isFalse();
                assertThat(rule.getTaxYear()).isEqualTo(2025);
                assertThat(rule.getParameterJsonb()).isNotNull();
            });

        // The real pack covers all major deduction types — confirm a representative sample made it through.
        assertThat(saved).extracting(DeductionRule::getDeductionType)
            .contains(
                DeductionType.PERSONAL_DEDUCTION,
                DeductionType.MEDICAL_EXPENSE,
                DeductionType.CREDIT_CARD,
                DeductionType.SME_YOUTH_EMPLOYEE_TAX_REDUCTION
            );

        assertThat(saved).extracting(DeductionRule::getRuleCode)
            .doesNotHaveDuplicates()
            .as("ruleCode uniqueness within a pack is critical for snapshot hashing");
    }

    private Path locateLawPack() {
        // Working dir under Gradle test is backend/; law-packs live one level up.
        Path candidate = Paths.get("../plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json");
        if (Files.exists(candidate)) {
            return candidate.toAbsolutePath().normalize();
        }
        // Fallback for IDE runs from repo root.
        Path alt = Paths.get("plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json");
        if (Files.exists(alt)) {
            return alt.toAbsolutePath().normalize();
        }
        throw new IllegalStateException(
            "Canonical 2025.01 law pack not found at expected paths. cwd=" + Paths.get("").toAbsolutePath()
        );
    }
}
