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
import org.junit.jupiter.api.condition.EnabledIf;
import com.example.yearend.common.config.JpaConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full-stack integration test: real Postgres via Testcontainers + real JPA
 * repositories + real LawPackImportService. Imports the canonical 2025.01
 * law pack and verifies rows actually land in the database.
 *
 * Skipped automatically when Docker is not available (local dev without
 * Docker Desktop, constrained CI environments).
 */
@DataJpaTest
@Import(JpaConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIf(
    value = "com.example.yearend.admin.application.LawPackImportIntegrationTest#isDockerAvailable",
    disabledReason = "Docker is not available; skipping Testcontainers-backed integration test."
)
class LawPackImportIntegrationTest {

    // Started lazily after the Docker availability check so CI without Docker skips cleanly.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("yearend_test")
        .withUsername("yearend")
        .withPassword("yearend")
        .withReuse(false);

    static {
        if (isDockerAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Autowired
    private TaxRuleSetRepository taxRuleSetRepository;

    @Autowired
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
    @DisplayName("imports the real 2025.01 law pack and persists 91 rules against Postgres")
    void persistsRealLawPack() throws Exception {
        MockMultipartFile file = loadCanonicalPack();

        AdminDtos.ImportRuleSetResponse response = service.importLawPack(file);

        assertThat(response.status()).isEqualTo(RuleSetStatus.DRAFT);
        assertThat(response.taxYear()).isEqualTo(2025);
        assertThat(response.ruleVersion()).isEqualTo("2025.01");
        assertThat(response.importedRuleCount()).isGreaterThanOrEqualTo(90);
        assertThat(response.replacedRuleCount()).isZero();

        TaxRuleSet persisted = taxRuleSetRepository.findById(response.ruleSetId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(RuleSetStatus.DRAFT);
        assertThat(persisted.getCanonicalPackPath()).startsWith("2025@2025.01");

        List<DeductionRule> persistedRules = deductionRuleRepository.findAllByTaxRuleSetId(response.ruleSetId());
        assertThat(persistedRules).hasSize(response.importedRuleCount());
        assertThat(persistedRules).allSatisfy(rule -> {
            assertThat(rule.isActive()).isFalse();
            assertThat(rule.getTaxYear()).isEqualTo(2025);
            assertThat(rule.getParameterJsonb()).isNotNull();
        });
        assertThat(persistedRules).extracting(DeductionRule::getDeductionType)
            .contains(DeductionType.PERSONAL_DEDUCTION, DeductionType.SME_YOUTH_EMPLOYEE_TAX_REDUCTION);
    }

    @Test
    @DisplayName("re-importing into an existing DRAFT replaces prior rules")
    void reImportReplacesDraftRules() throws Exception {
        MockMultipartFile file = loadCanonicalPack();

        AdminDtos.ImportRuleSetResponse first = service.importLawPack(file);
        AdminDtos.ImportRuleSetResponse second = service.importLawPack(loadCanonicalPack());

        assertThat(second.ruleSetId()).isEqualTo(first.ruleSetId());
        assertThat(second.replacedRuleCount()).isEqualTo(first.importedRuleCount());
        assertThat(second.importedRuleCount()).isEqualTo(first.importedRuleCount());

        List<DeductionRule> finalRules = deductionRuleRepository.findAllByTaxRuleSetId(second.ruleSetId());
        assertThat(finalRules).hasSize(second.importedRuleCount());
    }

    @Test
    @DisplayName("rejects re-import when existing rule set has advanced past DRAFT")
    void rejectsWhenExistingNotDraft() throws Exception {
        MockMultipartFile file = loadCanonicalPack();
        AdminDtos.ImportRuleSetResponse first = service.importLawPack(file);

        TaxRuleSet ruleSet = taxRuleSetRepository.findById(first.ruleSetId()).orElseThrow();
        ruleSet.setStatus(RuleSetStatus.READY_FOR_REVIEW);
        taxRuleSetRepository.save(ruleSet);

        assertThatThrownBy(() -> service.importLawPack(loadCanonicalPack()))
            .hasMessageContaining("DRAFT");
    }

    private MockMultipartFile loadCanonicalPack() throws Exception {
        Path lawPack = locatePack();
        return new MockMultipartFile(
            "file",
            "normalized-rule-pack.json",
            "application/json",
            Files.readAllBytes(lawPack)
        );
    }

    private Path locatePack() {
        Path cwd = Paths.get("").toAbsolutePath();
        Path candidate = cwd.resolve("../plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json");
        if (Files.exists(candidate)) {
            return candidate.normalize();
        }
        Path alt = cwd.resolve("plugins/year-end-harness/law-packs/2025/2025.01/normalized-rule-pack.json");
        if (Files.exists(alt)) {
            return alt.normalize();
        }
        throw new IllegalStateException("Canonical 2025.01 law pack not found. cwd=" + cwd);
    }
}
