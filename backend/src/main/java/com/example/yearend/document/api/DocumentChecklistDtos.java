package com.example.yearend.document.api;

import com.example.yearend.document.domain.DocumentType;
import com.example.yearend.document.domain.ReviewStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class DocumentChecklistDtos {

    private DocumentChecklistDtos() {
    }

    public record ChecklistResponse(
        UUID id,
        UUID deductionItemId,
        DocumentType documentType,
        boolean requiredYn,
        boolean submittedYn,
        ReviewStatus reviewStatus,
        String comment,
        OffsetDateTime reviewedAt
    ) {
    }
}
