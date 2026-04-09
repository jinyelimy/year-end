package com.example.yearend.deduction.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.deduction.application.HometaxParsingDtos.ImportReviewDecision;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import com.example.yearend.taxsession.domain.TaxSession;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HometaxPdfImportParser {

    private static final String PARSER_TYPE = "PDFBOX_PHASE1_SECTION_PARSER";
    private static final String OCR_PARSER_TYPE = "PDFBOX_PHASE1_SECTION_PARSER_OCR";

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?<!\\d)(\\d{1,3}(?:,\\d{3})+|\\d{5,})(?!\\d)");
    private static final Pattern BRACKET_TITLE_PATTERN = Pattern.compile("\\[(.+?)]");
    private static final Pattern RESIDENT_ID_PATTERN = Pattern.compile("(\\d{6}-(?:\\d{7}|\\*{7}))");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^-[0-9]+-$");

    public ParsedHometaxDocument parse(
        TaxSession session,
        MultipartFile file,
        String fileName,
        OffsetDateTime parsedAt
    ) {
        byte[] pdfBytes = readUploadedBytes(file);
        List<String> warnings = new ArrayList<>();

        List<ExtractedPageText> textLayerPages = extractPages(pdfBytes, warnings);
        boolean textLayerDetected = textLayerPages.stream().anyMatch(this::hasText);

        List<ExtractedPageText> effectivePages = textLayerPages;
        boolean ocrFallbackUsed = false;
        if (!textLayerDetected) {
            warnings.add("No text layer was detected in the uploaded PDF. Attempting OCR fallback.");
            List<ExtractedPageText> ocrPages = extractPagesWithOcr(pdfBytes, warnings);
            if (ocrPages.stream().anyMatch(this::hasText)) {
                effectivePages = ocrPages;
                ocrFallbackUsed = true;
            } else {
                warnings.add("OCR fallback did not return usable text. Manual review is required.");
            }
        }

        List<ParsedDeductionCandidate> candidates = (textLayerDetected || ocrFallbackUsed)
            ? parseCandidates(effectivePages, warnings)
            : List.of();

        if (candidates.isEmpty()) {
            warnings.add("No phase 1 deduction candidates were extracted from the document.");
        } else {
            warnings.add("Extracted " + candidates.size() + " phase 1 deduction candidates from the document.");
        }

        return new ParsedHometaxDocument(
            fileName,
            parsedAt,
            ocrFallbackUsed ? OCR_PARSER_TYPE : PARSER_TYPE,
            textLayerDetected,
            warnings,
            candidates
        );
    }

    private byte[] readUploadedBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to read the uploaded PDF file.");
        }
    }

    private List<ExtractedPageText> extractPages(byte[] pdfBytes, List<String> warnings) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");

            List<ExtractedPageText> pages = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                int pageNumber = pageIndex + 1;
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = normalizeDocumentText(stripper.getText(document));
                pages.add(new ExtractedPageText(pageNumber, text, splitLines(text)));
            }
            return pages;
        } catch (IOException exception) {
            warnings.add("PDF parsing failed with " + exception.getClass().getSimpleName() + ".");
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to extract text from the uploaded PDF.");
        }
    }

    private List<ExtractedPageText> extractPagesWithOcr(byte[] pdfBytes, List<String> warnings) {
        if (!isTesseractAvailable()) {
            warnings.add("OCR fallback is unavailable because the 'tesseract' command could not be found.");
            return List.of();
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            List<ExtractedPageText> pages = new ArrayList<>();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                int pageNumber = pageIndex + 1;
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 300);
                String text = runTesseract(image, pageNumber, warnings);
                String normalizedText = normalizeDocumentText(text);
                pages.add(new ExtractedPageText(pageNumber, normalizedText, splitLines(normalizedText)));
            }

            long extractedPageCount = pages.stream().filter(this::hasText).count();
            if (extractedPageCount > 0) {
                warnings.add("OCR fallback extracted text from " + extractedPageCount + " page(s).");
            }
            return pages;
        } catch (IOException exception) {
            warnings.add("OCR fallback failed with " + exception.getClass().getSimpleName() + ".");
            return List.of();
        }
    }

    private boolean isTesseractAvailable() {
        Process process = null;
        try {
            process = new ProcessBuilder(resolveTesseractCommand(), "--version")
                .redirectErrorStream(true)
                .start();
            process.getInputStream().readAllBytes();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private String runTesseract(BufferedImage image, int pageNumber, List<String> warnings) {
        Path imagePath = null;
        Process process = null;

        try {
            imagePath = Files.createTempFile("hometax-ocr-page-" + pageNumber + "-", ".png");
            ImageIO.write(image, "png", imagePath.toFile());

            process = new ProcessBuilder(
                resolveTesseractCommand(),
                imagePath.toString(),
                "stdout",
                "-l",
                "kor+eng",
                "--psm",
                "6"
            )
                .redirectErrorStream(true)
                .start();

            byte[] output = process.getInputStream().readAllBytes();
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                warnings.add("OCR timed out on page " + pageNumber + ".");
                return "";
            }
            if (process.exitValue() != 0) {
                warnings.add("OCR exited with code " + process.exitValue() + " on page " + pageNumber + ".");
                return "";
            }
            return new String(output, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            warnings.add("OCR failed on page " + pageNumber + " with " + exception.getClass().getSimpleName() + ".");
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            warnings.add("OCR was interrupted on page " + pageNumber + ".");
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
            if (imagePath != null) {
                try {
                    Files.deleteIfExists(imagePath);
                } catch (IOException ignored) {
                    // Ignore best-effort cleanup failure.
                }
            }
        }
    }

    private String resolveTesseractCommand() {
        String configured = System.getenv("TESSERACT_PATH");
        return StringUtils.hasText(configured) ? configured.trim() : "tesseract";
    }

    private boolean hasText(ExtractedPageText page) {
        return page != null && !page.lines().isEmpty();
    }

    private List<ParsedDeductionCandidate> parseCandidates(List<ExtractedPageText> pages, List<String> warnings) {
        List<ParsedDeductionCandidate> candidates = new ArrayList<>();

        for (ExtractedPageText page : pages) {
            parsePage(page, warnings).ifPresent(candidates::add);
        }

        candidates.sort(Comparator.comparingInt(candidate -> Optional.ofNullable(candidate.pageNumber()).orElse(0)));
        return candidates;
    }

    private Optional<ParsedDeductionCandidate> parsePage(ExtractedPageText page, List<String> warnings) {
        String sectionLabel = extractSectionLabel(page.lines()).orElse(null);
        if (!StringUtils.hasText(sectionLabel)) {
            return Optional.empty();
        }

        SectionDescriptor section = classifySection(sectionLabel);
        if (section == null) {
            return Optional.empty();
        }

        PersonInfo personInfo = extractPersonInfo(page.lines()).orElse(null);
        if (personInfo == null) {
            warnings.add("Skipped page " + page.pageNumber() + " because the page owner could not be identified.");
            return Optional.empty();
        }

        return switch (section.kind()) {
            case MEDICAL -> parseMedicalCandidate(page, section, personInfo, warnings);
            case INSURANCE -> parseInsuranceCandidate(page, section, personInfo, warnings);
            case EDUCATION -> parseEducationCandidate(page, section, personInfo, warnings);
            case CREDIT_CARD -> parseCreditCardCandidate(page, section, personInfo, warnings);
        };
    }

    private Optional<ParsedDeductionCandidate> parseMedicalCandidate(
        ExtractedPageText page,
        SectionDescriptor section,
        PersonInfo personInfo,
        List<String> warnings
    ) {
        List<String> bodyLines = extractSectionBody(page.lines(), List.of("의료비 지출내역"));
        AmountExtraction total = findExactTotal(bodyLines, "인별합계금액").orElse(null);
        if (total == null || total.amount() == 0L) {
            warnings.add("Skipped medical page " + page.pageNumber() + " because the per-person total was not found.");
            return Optional.empty();
        }

        return Optional.of(buildCandidate(page, section, personInfo, total.amount(), total.rawLine(), Map.of()));
    }

    private Optional<ParsedDeductionCandidate> parseInsuranceCandidate(
        ExtractedPageText page,
        SectionDescriptor section,
        PersonInfo personInfo,
        List<String> warnings
    ) {
        List<String> bodyLines = extractSectionBody(
            page.lines(),
            List.of(
                "보장성보험 납입내역",
                "보장성보험)납입내역",
                "장애인전용보장성보험)납입내역"
            )
        );
        AmountExtraction total = findExactTotal(bodyLines, "인별합계금액").orElse(null);
        if (total == null || total.amount() == 0L) {
            warnings.add("Skipped insurance page " + page.pageNumber() + " because the per-person total was not found.");
            return Optional.empty();
        }

        return Optional.of(buildCandidate(page, section, personInfo, total.amount(), total.rawLine(), Map.of()));
    }

    private Optional<ParsedDeductionCandidate> parseEducationCandidate(
        ExtractedPageText page,
        SectionDescriptor section,
        PersonInfo personInfo,
        List<String> warnings
    ) {
        List<String> bodyLines = extractSectionBody(
            page.lines(),
            List.of(
                "교육비 지출내역",
                "직업훈련비 납입내역",
                "교복구입비 지출내역",
                "학자금대출 원리금상환내역"
            )
        );

        List<String> totalLines = bodyLines.stream()
            .filter(this::isEducationTotalLine)
            .toList();

        long amount = totalLines.stream()
            .map(this::extractTailAmount)
            .flatMap(Optional::stream)
            .mapToLong(AmountExtraction::amount)
            .sum();

        if (amount == 0L) {
            warnings.add("Skipped education page " + page.pageNumber() + " because no education total was found.");
            return Optional.empty();
        }

        Map<String, Object> parsedAttributes = new LinkedHashMap<>();
        parsedAttributes.put("educationSubType", resolveEducationSubType(section.rawLabel(), bodyLines));
        parsedAttributes.put("educationTotalLines", totalLines);

        String rawLine = String.join(" | ", totalLines);
        return Optional.of(buildCandidate(page, section, personInfo, amount, rawLine, parsedAttributes));
    }

    private Optional<ParsedDeductionCandidate> parseCreditCardCandidate(
        ExtractedPageText page,
        SectionDescriptor section,
        PersonInfo personInfo,
        List<String> warnings
    ) {
        SummaryExtraction summary = extractCreditCardSummary(page.lines()).orElse(null);
        if (summary == null || summary.amount() == 0L) {
            warnings.add("Skipped card page " + page.pageNumber() + " because the summary total was not found.");
            return Optional.empty();
        }

        return Optional.of(buildCandidate(page, section, personInfo, summary.amount(), summary.rawLine(), summary.attributes()));
    }

    private ParsedDeductionCandidate buildCandidate(
        ExtractedPageText page,
        SectionDescriptor section,
        PersonInfo personInfo,
        long amount,
        String rawLine,
        Map<String, Object> parsedAttributes
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("personName", personInfo.name());
        attributes.put("personResidentId", personInfo.residentId());
        attributes.put("sectionKind", section.kind().name());
        attributes.put("sectionLabel", section.rawLabel());
        attributes.put("calculationSupported", section.calculationSupported());
        attributes.put("quickApproveSupported", section.quickApproveSupported());
        attributes.putAll(parsedAttributes);

        String subType = section.kind() == SectionKind.EDUCATION
            ? (String) attributes.getOrDefault("educationSubType", section.subType())
            : section.subType();

        return new ParsedDeductionCandidate(
            section.deductionType(),
            subType,
            amount,
            null,
            buildSourceName(personInfo.name(), section.displayLabel()),
            EvidenceStatus.SUBMITTED,
            ImportReviewDecision.needsReview("MEDIUM", section.reviewReason()),
            page.pageNumber(),
            section.rawLabel(),
            rawLine,
            attributes
        );
    }

    private Optional<AmountExtraction> findExactTotal(List<String> lines, String totalLabel) {
        String compactLabel = compact(totalLabel);

        for (int index = lines.size() - 1; index >= 0; index--) {
            String line = lines.get(index);
            if (!compact(line).startsWith(compactLabel)) {
                continue;
            }
            Optional<AmountExtraction> amount = extractTailAmount(line);
            if (amount.isPresent()) {
                return amount;
            }
        }

        return Optional.empty();
    }

    private boolean isEducationTotalLine(String line) {
        String compactLine = compact(line);
        return compactLine.contains("합계금액") && countAmounts(line) >= 1;
    }

    private Optional<SummaryExtraction> extractCreditCardSummary(List<String> lines) {
        int summaryHeaderIndex = findLineIndex(lines, List.of("신용카드 등 사용금액 집계", "현금영수증 사용금액 집계"));
        if (summaryHeaderIndex < 0) {
            return Optional.empty();
        }

        int nextSectionIndex = lines.size();
        for (int index = summaryHeaderIndex + 1; index < lines.size(); index++) {
            if (lines.get(index).startsWith("■")) {
                nextSectionIndex = index;
                break;
            }
        }

        String headerLine = null;
        String amountLine = null;
        for (int index = summaryHeaderIndex + 1; index < nextSectionIndex; index++) {
            String line = lines.get(index);
            if (headerLine == null && compact(line).contains("합계금액")) {
                headerLine = line;
                continue;
            }
            if (headerLine != null && countAmounts(line) >= 2) {
                amountLine = line;
                break;
            }
        }

        if (!StringUtils.hasText(headerLine) || !StringUtils.hasText(amountLine)) {
            return Optional.empty();
        }

        List<String> labels = extractSummaryLabels(headerLine);
        List<Long> amounts = extractAllAmounts(amountLine);
        long totalAmount = amounts.isEmpty() ? 0L : amounts.getLast();

        Map<String, Object> attributes = new LinkedHashMap<>();
        if (!labels.isEmpty() && labels.size() == amounts.size()) {
            Map<String, Long> categoryTotals = new LinkedHashMap<>();
            for (int index = 0; index < labels.size(); index++) {
                categoryTotals.put(labels.get(index), amounts.get(index));
            }
            attributes.put("categoryTotals", categoryTotals);
        }

        return Optional.of(new SummaryExtraction(totalAmount, headerLine + " => " + amountLine, attributes));
    }

    private List<String> extractSummaryLabels(String headerLine) {
        String normalized = normalizeLine(headerLine)
            .replace("(", " ")
            .replace(")", " ")
            .replace(":", " ");

        List<String> labels = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (StringUtils.hasText(token)) {
                labels.add(token);
            }
        }
        return labels;
    }

    private Optional<AmountExtraction> extractTailAmount(String line) {
        List<Long> amounts = extractAllAmounts(line);
        if (amounts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AmountExtraction(amounts.getLast(), normalizeLine(line)));
    }

    private List<Long> extractAllAmounts(String line) {
        Matcher matcher = AMOUNT_PATTERN.matcher(line);
        List<Long> amounts = new ArrayList<>();
        while (matcher.find()) {
            amounts.add(parseAmount(matcher.group(1)));
        }
        return amounts;
    }

    private long parseAmount(String amountText) {
        return Long.parseLong(amountText.replace(",", ""));
    }

    private int countAmounts(String line) {
        return extractAllAmounts(line).size();
    }

    private List<String> extractSectionBody(List<String> lines, List<String> headerKeywords) {
        int headerIndex = findLineIndex(lines, headerKeywords);
        if (headerIndex < 0) {
            return List.of();
        }

        List<String> body = new ArrayList<>();
        for (int index = headerIndex + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("■") || line.startsWith("일련번호") || PAGE_NUMBER_PATTERN.matcher(line).matches()) {
                break;
            }
            if (line.matches("^[0-9]+\\..*")) {
                break;
            }
            body.add(line);
        }
        return body;
    }

    private int findLineIndex(List<String> lines, List<String> keywords) {
        List<String> compactKeywords = keywords.stream()
            .map(this::compact)
            .toList();

        for (int index = 0; index < lines.size(); index++) {
            String compactLine = compact(lines.get(index));
            if (compactKeywords.stream().anyMatch(compactLine::contains)) {
                return index;
            }
        }
        return -1;
    }

    private Optional<String> extractSectionLabel(List<String> lines) {
        int limit = Math.min(lines.size(), 6);
        for (int index = 0; index < limit; index++) {
            Matcher matcher = BRACKET_TITLE_PATTERN.matcher(lines.get(index));
            if (matcher.find()) {
                return Optional.of(normalizeSectionLabel(matcher.group(1)));
            }
        }
        return Optional.empty();
    }

    private SectionDescriptor classifySection(String sectionLabel) {
        String compactLabel = compact(sectionLabel);

        if (compactLabel.equals("의료비")) {
            return new SectionDescriptor(
                SectionKind.MEDICAL,
                DeductionType.MEDICAL_EXPENSE,
                sectionLabel,
                "의료비",
                "MEDICAL_TOTAL",
                true,
                true,
                "Medical expense totals were extracted from the Hometax PDF. Please confirm reimbursements and eligibility."
            );
        }

        if (compactLabel.contains("보장성보험")) {
            return new SectionDescriptor(
                SectionKind.INSURANCE,
                DeductionType.INSURANCE,
                sectionLabel,
                "보장성 보험",
                "INSURANCE_PREMIUM",
                true,
                true,
                "Insurance premium totals were extracted from the Hometax PDF. Please confirm insured dependents and policy type."
            );
        }

        if (compactLabel.contains("교육비")
            || compactLabel.contains("직업훈련비")
            || compactLabel.contains("교복구입비")
            || compactLabel.contains("수능응시료")
            || compactLabel.contains("대학입학전형료")) {
            return new SectionDescriptor(
                SectionKind.EDUCATION,
                DeductionType.EDUCATION_EXPENSE,
                sectionLabel,
                resolveEducationDisplayLabel(compactLabel),
                "SCHOOL",
                true,
                true,
                "Education expense totals were extracted from the Hometax PDF. Please confirm the education subtype and deduction limit."
            );
        }

        if (compactLabel.contains("신용카드")) {
            return new SectionDescriptor(
                SectionKind.CREDIT_CARD,
                DeductionType.CREDIT_CARD,
                sectionLabel,
                "신용카드",
                "CREDIT_CARD",
                false,
                false,
                "Credit card usage totals were imported for review. Calculation engine support will be connected in phase 2."
            );
        }

        if (compactLabel.contains("직불카드등(제로페이사용분)")) {
            return new SectionDescriptor(
                SectionKind.CREDIT_CARD,
                DeductionType.CREDIT_CARD,
                sectionLabel,
                "제로페이",
                "ZERO_PAY",
                false,
                false,
                "Zero-pay usage totals were imported for review. Calculation engine support will be connected in phase 2."
            );
        }

        if (compactLabel.contains("직불카드등")) {
            return new SectionDescriptor(
                SectionKind.CREDIT_CARD,
                DeductionType.CREDIT_CARD,
                sectionLabel,
                "직불카드",
                "DEBIT_CARD",
                false,
                false,
                "Debit card usage totals were imported for review. Calculation engine support will be connected in phase 2."
            );
        }

        if (compactLabel.contains("현금영수증")) {
            return new SectionDescriptor(
                SectionKind.CREDIT_CARD,
                DeductionType.CREDIT_CARD,
                sectionLabel,
                "현금영수증",
                "CASH_RECEIPT",
                false,
                false,
                "Cash-receipt totals were imported for review. Calculation engine support will be connected in phase 2."
            );
        }

        return null;
    }

    private String resolveEducationDisplayLabel(String compactLabel) {
        if (compactLabel.contains("직업훈련비")) {
            return "직업훈련비";
        }
        if (compactLabel.contains("교복구입비")) {
            return "교복구입비";
        }
        if (compactLabel.contains("학자금대출")) {
            return "학자금대출 상환";
        }
        if (compactLabel.contains("수능응시료") || compactLabel.contains("대학입학전형료")) {
            return "입학전형료";
        }
        return "교육비";
    }

    private String resolveEducationSubType(String sectionLabel, List<String> bodyLines) {
        String compactLabel = compact(sectionLabel);
        String joined = compact(String.join(" ", bodyLines));

        if (compactLabel.contains("직업훈련비") || compactLabel.contains("학자금대출")) {
            return "SELF_EDUCATION";
        }
        if (compactLabel.contains("교복구입비")) {
            return "SCHOOL";
        }
        if (compactLabel.contains("수능응시료") || compactLabel.contains("대학입학전형료")) {
            return joined.contains("졸업예정") ? "SCHOOL" : "UNIVERSITY";
        }
        if (joined.contains("대학원") || joined.contains("전문대학") || joined.contains("대학교") || joined.contains("학점은행제")) {
            return "UNIVERSITY";
        }
        if (joined.contains("유치원")) {
            return "PRESCHOOL";
        }
        if (joined.contains("고등학교") || joined.contains("중학교") || joined.contains("초등학교") || joined.contains("현장체험학습비")) {
            return "SCHOOL";
        }
        if (joined.contains("학원")) {
            return "ACADEMY";
        }
        return "SCHOOL";
    }

    private Optional<PersonInfo> extractPersonInfo(List<String> lines) {
        int personIndex = findLineIndex(lines, List.of("인적사항"));
        if (personIndex < 0) {
            return Optional.empty();
        }

        int endExclusive = Math.min(lines.size(), personIndex + 6);
        for (int index = personIndex + 1; index < endExclusive; index++) {
            String line = normalizeLine(lines.get(index));
            Matcher matcher = RESIDENT_ID_PATTERN.matcher(line);
            if (!matcher.find()) {
                continue;
            }

            String residentId = matcher.group(1);
            String name = normalizeLine(line.substring(0, matcher.start()))
                .replace("성 명", "")
                .trim();
            if (StringUtils.hasText(name)) {
                return Optional.of(new PersonInfo(name, residentId));
            }
        }

        return Optional.empty();
    }

    private String buildSourceName(String personName, String displayLabel) {
        if (StringUtils.hasText(personName)) {
            return personName + " · " + displayLabel;
        }
        return displayLabel;
    }

    private String normalizeDocumentText(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').strip();
    }

    private List<String> splitLines(String text) {
        return text.lines()
            .map(this::normalizeLine)
            .filter(StringUtils::hasText)
            .toList();
    }

    private String normalizeLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
            .replace('\u3000', ' ')
            .trim()
            .replaceAll("\\s+", " ");
    }

    private String normalizeSectionLabel(String value) {
        return normalizeLine(value)
            .replace(" :", ":")
            .replace(": ", ":")
            .replace(" ,", ",")
            .replace(", ", ", ");
    }

    private String compact(String value) {
        return normalizeLine(value).replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private enum SectionKind {
        MEDICAL,
        INSURANCE,
        EDUCATION,
        CREDIT_CARD
    }

    private record ExtractedPageText(
        int pageNumber,
        String text,
        List<String> lines
    ) {
    }

    private record SectionDescriptor(
        SectionKind kind,
        DeductionType deductionType,
        String rawLabel,
        String displayLabel,
        String subType,
        boolean calculationSupported,
        boolean quickApproveSupported,
        String reviewReason
    ) {
    }

    private record PersonInfo(
        String name,
        String residentId
    ) {
    }

    private record AmountExtraction(
        long amount,
        String rawLine
    ) {
    }

    private record SummaryExtraction(
        long amount,
        String rawLine,
        Map<String, Object> attributes
    ) {
    }
}
