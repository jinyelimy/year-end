package com.example.yearend.deduction.application;

import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HometaxParsingDtos {

    private HometaxParsingDtos() {
    }

    public record ParsedHometaxDocument(
        String fileName,
        OffsetDateTime parsedAt,
        String parserType,
        boolean textLayerDetected,
        List<String> warnings,
        List<ParsedDeductionCandidate> candidates
    ) {
        public ParsedHometaxDocument {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record ImportReviewDecision(
        String importBucket,
        String reviewStatus,
        String confidenceLevel,
        String reviewReason
    ) {
        public ImportReviewDecision {
            Objects.requireNonNull(importBucket, "importBucket must not be null");
            Objects.requireNonNull(reviewStatus, "reviewStatus must not be null");
            Objects.requireNonNull(confidenceLevel, "confidenceLevel must not be null");
            Objects.requireNonNull(reviewReason, "reviewReason must not be null");
        }

        public static ImportReviewDecision autoApplied(String confidenceLevel, String reviewReason) {
            return new ImportReviewDecision("AUTO_APPLIED", "APPROVED", confidenceLevel, reviewReason);
        }

        public static ImportReviewDecision needsReview(String confidenceLevel, String reviewReason) {
            return new ImportReviewDecision("NEEDS_REVIEW", "PENDING", confidenceLevel, reviewReason);
        }

        public static ImportReviewDecision excluded(String confidenceLevel, String reviewReason) {
            return new ImportReviewDecision("EXCLUDED", "EXCLUDED", confidenceLevel, reviewReason);
        }

        public boolean isAutoApplied() {
            return "AUTO_APPLIED".equals(importBucket) && "APPROVED".equals(reviewStatus);
        }

        public boolean needsReview() {
            return !isAutoApplied();
        }

        public boolean includedInDocumentChecklist() {
            return !"PENDING".equals(reviewStatus) && !"EXCLUDED".equals(reviewStatus);
        }
    }

    public record ParsedDeductionCandidate(
        DeductionType deductionType,
        String subType,
        Long amount,
        LocalDate usedAt,
        String sourceName,
        EvidenceStatus evidenceStatus,
        ImportReviewDecision reviewDecision,
        Integer pageNumber,
        String rawSectionTitle,
        String rawLineText,
        Map<String, Object> parsedAttributes
    ) {
        public ParsedDeductionCandidate {
            parsedAttributes = parsedAttributes == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parsedAttributes));
        }

        public boolean isAutoApplied() {
            return reviewDecision.isAutoApplied();
        }

        public boolean needsReview() {
            return reviewDecision.needsReview();
        }

        public boolean includedInDocumentChecklist() {
            return reviewDecision.includedInDocumentChecklist();
        }
    }
}
