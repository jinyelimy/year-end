package com.example.yearend.deduction.application;

import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedDeductionCandidate;
import com.example.yearend.deduction.application.HometaxParsingDtos.ParsedHometaxDocument;
import com.example.yearend.deduction.application.HometaxParsingDtos.ImportReviewDecision;
import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HometaxParsingDtosTest {

    @Test
    @DisplayName("auto applied candidate keeps review helper flags in sync")
    void autoAppliedCandidate() {
        ParsedDeductionCandidate candidate = new ParsedDeductionCandidate(
            DeductionType.MEDICAL_EXPENSE,
            "Hospital bill",
            480_000L,
            LocalDate.of(2025, 1, 15),
            "Seoul General Hospital",
            EvidenceStatus.SUBMITTED,
            ImportReviewDecision.autoApplied("HIGH", "Matched cleanly"),
            1,
            "Medical expense details",
            "Medical expense / Seoul General Hospital / 480000"
        );

        assertThat(candidate.isAutoApplied()).isTrue();
        assertThat(candidate.needsReview()).isFalse();
        assertThat(candidate.includedInDocumentChecklist()).isTrue();
    }

    @Test
    @DisplayName("pending candidate stays review-only until confirmed")
    void pendingCandidate() {
        ParsedDeductionCandidate candidate = new ParsedDeductionCandidate(
            DeductionType.EDUCATION_EXPENSE,
            "Private academy tuition",
            2_400_000L,
            LocalDate.of(2025, 3, 4),
            "Mirae Academy",
            EvidenceStatus.PENDING,
            ImportReviewDecision.needsReview("MEDIUM", "School type needs manual review"),
            3,
            "Education expense details",
            "Education / Mirae Academy / 2400000"
        );

        assertThat(candidate.isAutoApplied()).isFalse();
        assertThat(candidate.needsReview()).isTrue();
        assertThat(candidate.includedInDocumentChecklist()).isFalse();
    }

    @Test
    @DisplayName("parsed document copies mutable lists defensively")
    void parsedDocumentCopiesLists() {
        List<String> warnings = new ArrayList<>();
        warnings.add("Sample warning");

        List<ParsedDeductionCandidate> candidates = new ArrayList<>();
        candidates.add(new ParsedDeductionCandidate(
            DeductionType.DONATION,
            "Donation receipt",
            150_000L,
            LocalDate.of(2025, 12, 22),
            "Sharing Foundation",
            EvidenceStatus.PENDING,
            ImportReviewDecision.needsReview("MEDIUM", "Certificate not checked yet"),
            4,
            "Donation details",
            "Donation / Sharing Foundation / 150000"
        ));

        ParsedHometaxDocument document = new ParsedHometaxDocument(
            "hometax.pdf",
            OffsetDateTime.parse("2026-03-30T09:00:00+09:00"),
            "PDFBOX_DRAFT",
            true,
            warnings,
            candidates
        );

        warnings.add("Mutated after creation");
        candidates.clear();

        assertThat(document.warnings()).containsExactly("Sample warning");
        assertThat(document.candidates()).hasSize(1);
    }

    @Test
    @DisplayName("excluded decision is kept out of document checklist sync")
    void excludedDecision() {
        ImportReviewDecision decision = ImportReviewDecision.excluded(
            "LOW",
            "The line could not be matched to a valid deduction rule"
        );

        assertThat(decision.isAutoApplied()).isFalse();
        assertThat(decision.needsReview()).isTrue();
        assertThat(decision.includedInDocumentChecklist()).isFalse();
    }
}
