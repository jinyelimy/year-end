package com.example.yearend.taxsession.api;

import com.example.yearend.taxsession.domain.RelationType;
import com.example.yearend.taxsession.domain.ResidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public final class DependentDtos {

    private DependentDtos() {
    }

    public record UpsertDependentRequest(
        @NotBlank
        @Size(max = 50)
        String name,

        @NotNull
        RelationType relationType,

        @NotNull
        @Past
        LocalDate birthDate,

        @NotNull
        @PositiveOrZero
        Long annualIncomeAmount,

        ResidentType residentType,
        boolean livesTogether,
        boolean disabled,
        boolean basicDeductionTarget
    ) {
    }

    public record DependentResponse(
        UUID id,
        String name,
        RelationType relationType,
        LocalDate birthDate,
        Long annualIncomeAmount,
        ResidentType residentType,
        boolean livesTogether,
        boolean disabled,
        boolean basicDeductionTarget
    ) {
    }
}
