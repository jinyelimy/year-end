package com.example.yearend.taxsession.api;

import com.example.yearend.taxsession.domain.IncomeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class IncomeItemDtos {

    private IncomeItemDtos() {
    }

    public record UpsertIncomeItemRequest(
        @NotNull
        IncomeType incomeType,

        @Size(max = 100)
        String payerName,

        @NotNull
        @PositiveOrZero
        Long grossAmount,

        @NotNull
        @PositiveOrZero
        Long taxableAmount,

        @NotNull
        @PositiveOrZero
        Long withheldTaxAmount,

        @NotNull
        @PositiveOrZero
        Long nonTaxableAmount,

        String attributesJsonb
    ) {
    }

    public record IncomeItemResponse(
        UUID id,
        IncomeType incomeType,
        String payerName,
        Long grossAmount,
        Long taxableAmount,
        Long withheldTaxAmount,
        Long nonTaxableAmount,
        String attributesJsonb
    ) {
    }
}
