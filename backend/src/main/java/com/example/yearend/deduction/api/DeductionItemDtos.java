package com.example.yearend.deduction.api;

import com.example.yearend.deduction.domain.DeductionType;
import com.example.yearend.deduction.domain.EvidenceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class DeductionItemDtos {

    private DeductionItemDtos() {
    }

    public record UpsertDeductionItemRequest(
        @NotNull
        DeductionType deductionType,

        UUID dependentId,

        @Size(max = 50)
        String subType,

        @NotNull
        @PositiveOrZero
        Long amount,

        LocalDate usedAt,

        @Size(max = 100)
        String sourceName,

        EvidenceStatus evidenceStatus,

        String attributesJsonb
    ) {
    }

    public record DeductionItemResponse(
        UUID id,
        DeductionType deductionType,
        UUID dependentId,
        String subType,
        Long amount,
        LocalDate usedAt,
        String sourceName,
        EvidenceStatus evidenceStatus,
        String attributesJsonb
    ) {
    }
}
