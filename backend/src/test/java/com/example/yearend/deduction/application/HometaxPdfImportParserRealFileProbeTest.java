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
    @DisplayName("parses the insurance total from the real workspace hometax pdf")
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
            OffsetDateTime.parse("2026-04-01T15:30:00+09:00")
        );

        assertThat(document.textLayerDetected()).isTrue();
        assertThat(document.candidates()).hasSize(1);

        ParsedDeductionCandidate candidate = document.candidates().getFirst();
        assertThat(candidate.deductionType()).isEqualTo(DeductionType.INSURANCE);
        assertThat(candidate.amount()).isEqualTo(2_545_170L);
        assertThat(candidate.pageNumber()).isEqualTo(2);
        assertThat(candidate.rawLineText()).contains("총합계 2,545,170");
        assertThat(candidate.rawSectionTitle()).contains("건강보험료");
        assertThat(candidate.sourceName()).contains("건강보험료");
        assertThat(candidate.sourceName()).doesNotContain("①", "②", "③", "④");
        assertThat(candidate.reviewDecision().reviewStatus()).isEqualTo("PENDING");
    }
}
