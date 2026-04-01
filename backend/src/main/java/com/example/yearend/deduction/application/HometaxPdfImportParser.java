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
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Component
public class HometaxPdfImportParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?<!\\d)(\\d{1,3}(?:[,.]\\d{3})+|\\d{5,})(?:\\s*원)?(?!\\d)"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(?<!\\d)(20\\d{2})\\s*[./-]\\s*(\\d{1,2})\\s*[./-]\\s*(\\d{1,2})(?!\\d)"
    );
    private static final Pattern RESIDENT_ID_PATTERN = Pattern.compile("\\b\\d{6}-\\d{7}\\b");
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^-[0-9]+-$");
    private static final Pattern MONTH_LINE_PATTERN = Pattern.compile("(^|\\s)\\d{1,2}월($|\\s)");
    private static final Pattern CIRCLED_NUMBER_PATTERN = Pattern.compile("[①②③④⑤⑥⑦⑧⑨⑩]");

    private static final List<CandidateRule> CANDIDATE_RULES = List.of(
        new CandidateRule(
            DeductionType.MEDICAL_EXPENSE,
            "의료비",
            List.of("의료비", "의료", "병원", "medical expense")
        ),
        new CandidateRule(
            DeductionType.INSURANCE,
            "보험료",
            List.of("건강보험료", "보험료", "보험", "insurance", "premium")
        ),
        new CandidateRule(
            DeductionType.EDUCATION_EXPENSE,
            "교육비",
            List.of("교육비", "수업료", "학원", "학교", "education", "tuition")
        ),
        new CandidateRule(
            DeductionType.DONATION,
            "기부금",
            List.of("기부금", "기부", "donation")
        )
    );

    public ParsedHometaxDocument parse(
        TaxSession session,
        MultipartFile file,
        String fileName,
        OffsetDateTime parsedAt
    ) {
        byte[] pdfBytes;
        try {
            pdfBytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to read the uploaded PDF file.");
        }

        List<String> warnings = new ArrayList<>();
        List<ExtractedPageText> pages = extractPages(pdfBytes, warnings);
        boolean textLayerDetected = pages.stream().anyMatch(page -> !page.lines().isEmpty());

        if (!textLayerDetected) {
            warnings.add("텍스트 레이어를 찾지 못했습니다. 스캔 PDF라면 OCR 연결이 추가로 필요합니다.");
        }

        List<ParsedDeductionCandidate> candidates = textLayerDetected
            ? selectTopCandidates(session, pages, warnings)
            : List.of();

        if (!candidates.isEmpty()) {
            warnings.add("현재 4/1 PoC에서는 점수가 가장 높은 공제 후보 1건만 가져옵니다.");
        } else if (textLayerDetected) {
            warnings.add("텍스트 추출에는 성공했지만 현재 1차 규칙으로는 공제 후보를 찾지 못했습니다.");
        }

        return new ParsedHometaxDocument(
            fileName,
            parsedAt,
            "PDFBOX_TEXT_LAYER_POC",
            textLayerDetected,
            warnings,
            candidates
        );
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
                String text = normalizeWhitespace(stripper.getText(document));
                pages.add(new ExtractedPageText(pageNumber, text, splitLines(text)));
            }
            return pages;
        } catch (IOException exception) {
            warnings.add("PDF 파싱 중 오류가 발생했습니다: " + exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to extract text from the uploaded PDF.");
        }
    }

    private List<ParsedDeductionCandidate> selectTopCandidates(
        TaxSession session,
        List<ExtractedPageText> pages,
        List<String> warnings
    ) {
        List<ScoredCandidate> matches = collectCandidates(session, pages);
        if (matches.isEmpty()) {
            return List.of();
        }

        ScoredCandidate topMatch = matches.stream()
            .max(Comparator
                .comparingInt(ScoredCandidate::score)
                .thenComparingLong(candidate -> candidate.parsedCandidate().amount())
                .thenComparingInt(candidate -> -candidate.parsedCandidate().pageNumber()))
            .orElseThrow();

        if (topMatch.excludedResidentIdLine()) {
            warnings.add("주민등록번호처럼 보이는 숫자 라인은 후보에서 제외했습니다.");
        }

        return List.of(topMatch.parsedCandidate());
    }

    private List<ScoredCandidate> collectCandidates(TaxSession session, List<ExtractedPageText> pages) {
        List<ScoredCandidate> matches = new ArrayList<>();

        for (ExtractedPageText page : pages) {
            CandidateRule currentSectionRule = null;
            String currentSectionTitle = null;

            for (String line : page.lines()) {
                CandidateRule explicitRule = detectRule(line).orElse(null);
                if (explicitRule != null) {
                    currentSectionRule = explicitRule;
                    if (isMeaningfulSectionTitle(line)) {
                        currentSectionTitle = line;
                    }
                }

                CandidateRule activeRule = explicitRule != null ? explicitRule : currentSectionRule;
                if (activeRule == null) {
                    continue;
                }

                Optional<ScoredCandidate> candidate = buildCandidate(
                    session,
                    page.pageNumber(),
                    currentSectionTitle != null ? currentSectionTitle : activeRule.subType(),
                    line,
                    activeRule
                );
                candidate.ifPresent(matches::add);
            }
        }

        return matches;
    }

    private Optional<ScoredCandidate> buildCandidate(
        TaxSession session,
        int pageNumber,
        String sectionTitle,
        String line,
        CandidateRule rule
    ) {
        if (shouldSkipLine(line)) {
            return Optional.empty();
        }

        Optional<Long> amount = extractAmount(line);
        if (amount.isEmpty()) {
            return Optional.empty();
        }

        int score = scoreLine(line, sectionTitle, rule);
        if (score < 40) {
            return Optional.empty();
        }

        LocalDate usedAt = extractDate(line).orElse(null);
        String sourceName = extractSourceName(sectionTitle, line, rule, amount.get(), usedAt);

        ParsedDeductionCandidate candidate = new ParsedDeductionCandidate(
            rule.deductionType(),
            rule.subType(),
            amount.get(),
            usedAt,
            sourceName,
            EvidenceStatus.SUBMITTED,
            ImportReviewDecision.needsReview(
                "MEDIUM",
                "실제 PDF 텍스트에서 추출한 1차 후보입니다. 금액과 공제 항목을 검토한 뒤 승인 여부를 결정해 주세요."
            ),
            pageNumber,
            sectionTitle,
            line
        );

        return Optional.of(new ScoredCandidate(candidate, score, false));
    }

    private Optional<CandidateRule> detectRule(String line) {
        String normalized = normalizeForMatch(line);
        String compact = compactForMatch(line);
        return CANDIDATE_RULES.stream()
            .filter(rule -> rule.keywords().stream().anyMatch(keyword ->
                normalized.contains(keyword.toLowerCase(Locale.ROOT))
                    || compact.contains(keyword.replace(" ", "").toLowerCase(Locale.ROOT))
            ))
            .findFirst();
    }

    private boolean shouldSkipLine(String line) {
        String normalized = normalizeForMatch(line);
        String compact = compactForMatch(line);

        return RESIDENT_ID_PATTERN.matcher(line).find()
            || PAGE_NUMBER_PATTERN.matcher(line.trim()).matches()
            || containsAny(normalized, compact, List.of(
                "주민등록번호",
                "주민등록",
                "인적사항",
                "조회기간",
                "일련번호",
                "조회되지않는내역",
                "소득공제대상금액",
                "영수증발급기관"
            ));
    }

    private Optional<Long> extractAmount(String line) {
        return AMOUNT_PATTERN.matcher(line)
            .results()
            .map(MatchResult::group)
            .map(this::parseAmount)
            .filter(amount -> amount >= 10_000L)
            .max(Comparator.naturalOrder());
    }

    private long parseAmount(String amountText) {
        String digitsOnly = amountText.replaceAll("[^0-9]", "");
        return digitsOnly.isBlank() ? 0L : Long.parseLong(digitsOnly);
    }

    private int scoreLine(String line, String sectionTitle, CandidateRule rule) {
        String normalized = normalizeForMatch(line);
        String compact = compactForMatch(line);
        String sectionNormalized = normalizeForMatch(sectionTitle);
        String sectionCompact = compactForMatch(sectionTitle);

        int score = 20;
        if (StringUtils.hasText(sectionTitle)) {
            score += 20;
        }
        if (containsAny(sectionNormalized, sectionCompact, rule.keywords())) {
            score += 15;
        }
        if (containsAny(normalized, compact, rule.keywords())) {
            score += 30;
        }
        if (containsAny(normalized, compact, List.of("총합계", "grand total"))) {
            score += 140;
        } else if (containsAny(normalized, compact, List.of("합계", "subtotal", "total"))) {
            score += 90;
        }
        if (containsAny(normalized, compact, List.of("연말정산"))) {
            score += 20;
        }
        if (MONTH_LINE_PATTERN.matcher(line).find()) {
            score -= 25;
        }
        if (countAmounts(line) >= 3 && !containsAny(normalized, compact, List.of("합계", "총합계"))) {
            score -= 15;
        }
        if (containsAny(normalized, compact, List.of("단위:원", "내역", "조회기간"))) {
            score -= 10;
        }

        return score;
    }

    private int countAmounts(String line) {
        return (int) AMOUNT_PATTERN.matcher(line).results().count();
    }

    private Optional<LocalDate> extractDate(String line) {
        java.util.regex.Matcher matcher = DATE_PATTERN.matcher(line);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        int day = Integer.parseInt(matcher.group(3));

        try {
            return Optional.of(LocalDate.of(year, month, day));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String extractSourceName(
        String sectionTitle,
        String line,
        CandidateRule rule,
        long amount,
        LocalDate usedAt
    ) {
        String fromLine = cleanupSource(line, amount, usedAt, rule);
        if (StringUtils.hasText(fromLine) && !isGenericSummaryWord(fromLine)) {
            return truncate(fromLine);
        }

        String fromSection = cleanupSectionTitle(sectionTitle, usedAt);
        if (StringUtils.hasText(fromSection)) {
            return truncate(fromSection);
        }

        return rule.subType() + " 추출 1건";
    }

    private String cleanupSource(String raw, long amount, LocalDate usedAt, CandidateRule rule) {
        String cleaned = normalizeWhitespace(raw);
        cleaned = cleaned.replace(Long.toString(amount), " ");
        cleaned = cleaned.replace(String.format("%,d", amount), " ");
        cleaned = cleaned.replace("원", " ");
        if (usedAt != null) {
            cleaned = cleaned.replace(usedAt.toString(), " ");
            cleaned = cleaned.replace(usedAt.toString().replace("-", "."), " ");
            cleaned = cleaned.replace(usedAt.toString().replace("-", "/"), " ");
        }
        for (String keyword : rule.keywords()) {
            cleaned = cleaned.replace(keyword, " ");
        }
        cleaned = cleaned
            .replace("총합계", " ")
            .replace("합계", " ")
            .replace("연말정산", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("(", " ")
            .replace(")", " ");
        cleaned = CIRCLED_NUMBER_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("(?i)\\bgrand total\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\bsubtotal\\b", " ");
        cleaned = cleaned.replaceAll("(?i)\\btotal\\b", " ");
        cleaned = cleaned.replaceAll("[,:|·]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String cleanupSectionTitle(String raw, LocalDate usedAt) {
        String cleaned = normalizeWhitespace(raw);
        if (usedAt != null) {
            cleaned = cleaned.replace(usedAt.toString(), " ");
            cleaned = cleaned.replace(usedAt.toString().replace("-", "."), " ");
            cleaned = cleaned.replace(usedAt.toString().replace("-", "/"), " ");
        }

        cleaned = cleaned
            .replace("2025년 귀속 소득", " ")
            .replace("세액공제증명서류", " ")
            .replace("기본내역", " ")
            .replace("내역", " ")
            .replace("단위:원", " ")
            .replace("단위원", " ")
            .replace("[", " ")
            .replace("]", " ")
            .replace("(", " ")
            .replace(")", " ");
        cleaned = CIRCLED_NUMBER_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("[,:|·]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private boolean isMeaningfulSectionTitle(String line) {
        String normalized = normalizeForMatch(line);
        String compact = compactForMatch(line);

        if (CIRCLED_NUMBER_PATTERN.matcher(line).find()) {
            return false;
        }

        return !containsAny(normalized, compact, List.of(
            "월별",
            "고지금액",
            "납부금액",
            "건강보험료①",
            "장기요양보험료②",
            "건강보험료③",
            "장기요양보험료④"
        ));
    }

    private boolean isGenericSummaryWord(String value) {
        String compact = compactForMatch(value);
        return compact.isBlank()
            || compact.equals("총합계")
            || compact.equals("합계")
            || compact.equals("연말정산")
            || compact.equals("grandtotal")
            || compact.equals("total")
            || compact.equals("subtotal");
    }

    private String truncate(String value) {
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private boolean containsAny(String normalized, String compact, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> {
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            String compactKeyword = lowerKeyword.replace(" ", "");
            return normalized.contains(lowerKeyword) || compact.contains(compactKeyword);
        });
    }

    private List<String> splitLines(String text) {
        return text.lines()
            .map(this::normalizeWhitespace)
            .filter(StringUtils::hasText)
            .toList();
    }

    private String normalizeWhitespace(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private String normalizeForMatch(String value) {
        return normalizeWhitespace(value).toLowerCase(Locale.ROOT);
    }

    private String compactForMatch(String value) {
        return normalizeForMatch(value).replace(" ", "");
    }

    private record ExtractedPageText(
        int pageNumber,
        String text,
        List<String> lines
    ) {
    }

    private record CandidateRule(
        DeductionType deductionType,
        String subType,
        List<String> keywords
    ) {
    }

    private record ScoredCandidate(
        ParsedDeductionCandidate parsedCandidate,
        int score,
        boolean excludedResidentIdLine
    ) {
    }
}
