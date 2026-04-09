package com.example.yearend.deduction.application;

import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.taxsession.domain.TaxSession;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HometaxPdfImportParserRealFileProbeTest {

    private final HometaxPdfImportParser parser = new HometaxPdfImportParser();

    @Test
    @DisplayName("extracts representative phase 1 candidates from the real workspace hometax pdf")
    void probeRealWorkspacePdf() throws IOException {
        Path pdfPath = Path.of("..", "고길동(750101)-2025년도자료.pdf").normalize();
        Assumptions.assumeTrue(
            Files.exists(pdfPath),
            () -> "Expected sample pdf at " + pdfPath.toAbsolutePath()
        );

        TaxSession session = new TaxSession();
        session.setTaxYear(2025);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            pdfPath.getFileName().toString(),
            "application/pdf",
            Files.readAllBytes(pdfPath)
        );

        ParsedHometaxDocument document = parser.parse(
            session,
            file,
            file.getOriginalFilename(),
            OffsetDateTime.parse("2026-04-03T15:30:00+09:00")
        );

        assertThat(document.textLayerDetected()).isTrue();
        assertThat(document.candidates()).hasSizeGreaterThanOrEqualTo(15);

        ParsedDeductionCandidate insurance = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.INSURANCE)
            .filter(candidate -> candidate.pageNumber() == 5)
            .findFirst()
            .orElseThrow();
        assertThat(insurance.amount()).isEqualTo(3_204_690L);
        assertThat(insurance.sourceName()).contains("고길동");

        ParsedDeductionCandidate medical = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.MEDICAL_EXPENSE)
            .filter(candidate -> candidate.pageNumber() == 8)
            .findFirst()
            .orElseThrow();
        assertThat(medical.amount()).isEqualTo(13_663_400L);
        assertThat(medical.parsedAttributes()).containsEntry("personName", "고길동");

        ParsedDeductionCandidate education = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.EDUCATION_EXPENSE)
            .filter(candidate -> candidate.pageNumber() == 20)
            .findFirst()
            .orElseThrow();
        assertThat(education.amount()).isEqualTo(9_700_000L);
        assertThat(education.subType()).isEqualTo("UNIVERSITY");

        ParsedDeductionCandidate creditCard = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.CREDIT_CARD)
            .filter(candidate -> candidate.pageNumber() == 30)
            .findFirst()
            .orElseThrow();
        assertThat(creditCard.amount()).isEqualTo(13_369_197L);
        assertThat(creditCard.parsedAttributes()).containsEntry("calculationSupported", false);
    }
}
