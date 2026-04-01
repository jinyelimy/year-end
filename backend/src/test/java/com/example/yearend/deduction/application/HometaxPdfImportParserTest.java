package com.example.yearend.deduction.application;

import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.taxsession.domain.TaxSession;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HometaxPdfImportParserTest {

    private final HometaxPdfImportParser parser = new HometaxPdfImportParser();

    @Test
    @DisplayName("extracts one deduction candidate from a text-layer PDF")
    void parseTextLayerPdf() throws IOException {
        TaxSession session = new TaxSession();
        session.setTaxYear(2025);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "hometax-medical.pdf",
            "application/pdf",
            createPdf(List.of(
                "Medical expense",
                "Seoul General Hospital 2025-01-15 480,000"
            ))
        );

        ParsedHometaxDocument document = parser.parse(
            session,
            file,
            file.getOriginalFilename(),
            OffsetDateTime.parse("2026-04-01T10:00:00+09:00")
        );

        assertThat(document.textLayerDetected()).isTrue();
        assertThat(document.parserType()).isEqualTo("PDFBOX_TEXT_LAYER_POC");
        assertThat(document.candidates()).hasSize(1);

        ParsedDeductionCandidate candidate = document.candidates().getFirst();
        assertThat(candidate.deductionType()).isEqualTo(DeductionType.MEDICAL_EXPENSE);
        assertThat(candidate.amount()).isEqualTo(480_000L);
        assertThat(candidate.usedAt()).isEqualTo(LocalDate.of(2025, 1, 15));
        assertThat(candidate.sourceName()).contains("Seoul General Hospital");
        assertThat(candidate.pageNumber()).isEqualTo(1);
        assertThat(candidate.rawLineText()).contains("480,000");
        assertThat(candidate.needsReview()).isTrue();
        assertThat(document.warnings()).anyMatch(warning -> warning.contains("점수가 가장 높은"));
    }

    @Test
    @DisplayName("prefers insurance total amount over resident number or monthly rows")
    void prefersInsuranceTotalAmount() throws IOException {
        TaxSession session = new TaxSession();
        session.setTaxYear(2025);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "hometax-insurance.pdf",
            "application/pdf",
            createPdf(List.of(
                "Insurance premium report",
                "Name 750101-1234567",
                "Insurance premium details",
                "01 month 155,280 11,530 0 0",
                "02 month 155,280 11,530 0 0",
                "Total 1,865,730 149,010 489,840 40,590",
                "Grand total 2,545,170"
            ))
        );

        ParsedHometaxDocument document = parser.parse(
            session,
            file,
            file.getOriginalFilename(),
            OffsetDateTime.parse("2026-04-01T11:30:00+09:00")
        );

        assertThat(document.textLayerDetected()).isTrue();
        assertThat(document.candidates()).hasSize(1);

        ParsedDeductionCandidate candidate = document.candidates().getFirst();
        assertThat(candidate.deductionType()).isEqualTo(DeductionType.INSURANCE);
        assertThat(candidate.amount()).isEqualTo(2_545_170L);
        assertThat(candidate.pageNumber()).isEqualTo(1);
        assertThat(candidate.rawLineText()).contains("Grand total 2,545,170");
        assertThat(candidate.sourceName()).contains("Insurance premium");
    }

    @Test
    @DisplayName("returns no candidate when text layer is missing")
    void parseBlankPdf() throws IOException {
        TaxSession session = new TaxSession();
        session.setTaxYear(2025);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "blank.pdf",
            "application/pdf",
            createBlankPdf()
        );

        ParsedHometaxDocument document = parser.parse(
            session,
            file,
            file.getOriginalFilename(),
            OffsetDateTime.parse("2026-04-01T10:00:00+09:00")
        );

        assertThat(document.textLayerDetected()).isFalse();
        assertThat(document.candidates()).isEmpty();
        assertThat(document.warnings()).anyMatch(warning -> warning.contains("OCR"));
    }

    private byte[] createPdf(List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 760);

                for (String line : lines) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -18);
                }

                contentStream.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createBlankPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
