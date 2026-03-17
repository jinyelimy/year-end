package com.example.yearend.taxsession.api;

import com.example.yearend.taxsession.domain.FilingType;
import com.example.yearend.taxsession.domain.SessionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class TaxSessionDtos {

    private TaxSessionDtos() {
    }

    public record CreateTaxSessionRequest(
        @NotNull
        @Min(2020)
        @Max(2100)
        Integer taxYear,

        FilingType filingType,

        @NotBlank
        @Size(max = 20)
        String ruleVersion
    ) {
    }

    public record UpdateBasicInfoRequest(
        @NotBlank
        String basicInfoJsonb,

        @Size(max = 1000)
        String memo
    ) {
    }

    public record TaxSessionResponse(
        UUID id,
        Integer taxYear,
        SessionStatus sessionStatus,
        FilingType filingType,
        String ruleVersion,
        String basicInfoJsonb,
        String memo,
        OffsetDateTime submittedAt
    ) {
    }
}
