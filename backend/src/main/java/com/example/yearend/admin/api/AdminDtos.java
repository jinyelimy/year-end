package com.example.yearend.admin.api;

import com.example.yearend.document.domain.DocumentType;
import com.example.yearend.document.domain.ReviewStatus;
import com.example.yearend.taxsession.domain.SessionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminSessionResponse(
        UUID sessionId,
        UUID userId,
        String userName,
        Integer taxYear,
        SessionStatus sessionStatus,
        OffsetDateTime submittedAt
    ) {
    }

    public record ReviewChecklistRequest(
        @NotNull
        ReviewStatus reviewStatus,

        @Size(max = 1000)
        String comment
    ) {
    }

    public record AdminChecklistResponse(
        UUID id,
        UUID sessionId,
        UUID deductionItemId,
        DocumentType documentType,
        boolean requiredYn,
        boolean submittedYn,
        ReviewStatus reviewStatus,
        String comment,
        OffsetDateTime reviewedAt,
        UUID reviewedBy
    ) {
    }
}
