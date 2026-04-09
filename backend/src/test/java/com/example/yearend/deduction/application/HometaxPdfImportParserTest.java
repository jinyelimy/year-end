package com.example.yearend.deduction.application;

import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.taxsession.domain.TaxSession;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HometaxPdfImportParserTest {

    private static final Path KOREAN_FONT_PATH = Path.of("C:\\Windows\\Fonts\\malgun.ttf");

    private final HometaxPdfImportParser parser = new HometaxPdfImportParser();

    @Test
    @DisplayName("extracts phase 1 candidates from multiple section pages")
    void parsePhaseOneSections() throws IOException {
        TaxSession session = new TaxSession();
        session.setTaxYear(2025);

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "hometax-phase1.pdf",
            "application/pdf",
            createPdf(List.of(
                List.of(
                    "2025년 귀속 소득 · 세액공제증명서류 : 기본(지출처별)내역",
                    "[보장성 보험, 장애인전용보장성보험]",
                    "■ 계약자 인적사항",
                    "성 명 주민등록번호",
                    "홍부인 810101-2234567",
                    "■ 보장성보험(장애인전용보장성보험)납입내역 (단위:원)",
                    "보장성 104-91-36225 홍부인 1,722,730",
                    "인별합계금액 1,722,730"
                ),
                List.of(
                    "2025년 귀속 소득 · 세액공제증명서류 : 기본(지출처별)내역 [의료비]",
                    "■ 환자 인적사항",
                    "성 명 주민등록번호",
                    "고길동 750101-1234567",
                    "■ 의료비 지출내역 (단위:원)",
                    "노블엘르산후조리원 산후조리원 비용 4,350,000",
                    "의료비 인별합계금액 58,730",
                    "산후조리원 인별합계금액 4,350,000",
                    "인별합계금액 4,408,730"
                ),
                List.of(
                    "2025년 귀속 소득 · 세액공제증명서류 : 기본(지출처별)내역",
                    "[교육 비]",
                    "■ 학생 인적사항",
                    "성 명 주민등록번호",
                    "고철수 050101-3234567",
                    "■ 교육비 지출내역 (단위:원)",
                    "고등학교 밀알학교 일반교육비 55,300",
                    "고등학교 밀알학교 현장체험학습비 153,480",
                    "일반교육비 합계금액 55,300",
                    "현장학습비 합계금액 153,480"
                ),
                List.of(
                    "2025년 귀속 소득 · 세액공제증명서류 : 기본(사용처별)내역",
                    "[ 신용 카드 ]",
                    "■ 사용자 인적사항",
                    "성 명 주민등록번호",
                    "고길동 750101-1234567",
                    "■ 신용카드 등 사용금액 집계",
                    "(단위:원)",
                    "일반 전통시장 대중교통 문화체육 합계금액",
                    "12,551,657 480,000 83,540 254,000 13,369,197"
                )
            ))
        );

        ParsedHometaxDocument document = parser.parse(
            session,
            file,
            file.getOriginalFilename(),
            OffsetDateTime.parse("2026-04-03T10:00:00+09:00")
        );

        assertThat(document.textLayerDetected()).isTrue();
        assertThat(document.parserType()).isEqualTo("PDFBOX_PHASE1_SECTION_PARSER");
        assertThat(document.candidates()).hasSize(4);

        ParsedDeductionCandidate insurance = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.INSURANCE)
            .findFirst()
            .orElseThrow();
        assertThat(insurance.amount()).isEqualTo(1_722_730L);
        assertThat(insurance.pageNumber()).isEqualTo(1);
        assertThat(insurance.sourceName()).contains("홍부인");

        ParsedDeductionCandidate medical = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.MEDICAL_EXPENSE)
            .findFirst()
            .orElseThrow();
        assertThat(medical.amount()).isEqualTo(4_408_730L);
        assertThat(medical.pageNumber()).isEqualTo(2);
        assertThat(medical.rawLineText()).contains("인별합계금액 4,408,730");

        ParsedDeductionCandidate education = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.EDUCATION_EXPENSE)
            .findFirst()
            .orElseThrow();
        assertThat(education.amount()).isEqualTo(208_780L);
        assertThat(education.subType()).isEqualTo("SCHOOL");
        assertThat(education.parsedAttributes()).containsEntry("personName", "고철수");

        ParsedDeductionCandidate creditCard = document.candidates().stream()
            .filter(candidate -> candidate.deductionType() == DeductionType.CREDIT_CARD)
            .findFirst()
            .orElseThrow();
        assertThat(creditCard.amount()).isEqualTo(13_369_197L);
        assertThat(creditCard.subType()).isEqualTo("CREDIT_CARD");
        assertThat(creditCard.parsedAttributes()).containsEntry("calculationSupported", false);
        assertThat(creditCard.parsedAttributes()).containsEntry("quickApproveSupported", false);
        @SuppressWarnings("unchecked")
        Map<String, Long> categoryTotals = (Map<String, Long>) creditCard.parsedAttributes().get("categoryTotals");
        assertThat(categoryTotals).containsEntry("합계금액", 13_369_197L);

        assertThat(document.warnings()).anyMatch(warning -> warning.contains("Extracted 4 phase 1 deduction candidates"));
    }

    @Test
    @DisplayName("returns no candidate when text layer is missing and OCR fallback is unavailable")
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
            OffsetDateTime.parse("2026-04-03T10:00:00+09:00")
        );

        assertThat(document.textLayerDetected()).isFalse();
        assertThat(document.candidates()).isEmpty();
        assertThat(document.warnings()).anyMatch(warning -> warning.contains("OCR"));
    }

    private byte[] createPdf(List<List<String>> pages) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDType0Font font = null;
            if (Files.exists(KOREAN_FONT_PATH)) {
                try (var fontStream = Files.newInputStream(KOREAN_FONT_PATH)) {
                    font = PDType0Font.load(document, fontStream);
                }
            }

            for (List<String> pageLines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    if (font != null) {
                        contentStream.setFont(font, 11);
                    } else {
                        throw new IOException("Korean font file is required for parser test PDF generation.");
                    }
                    contentStream.newLineAtOffset(48, 790);

                    for (String line : pageLines) {
                        contentStream.showText(line);
                        contentStream.newLineAtOffset(0, -18);
                    }

                    contentStream.endText();
                }
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
