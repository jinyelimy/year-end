package com.example.yearend.user.api;

import com.example.yearend.user.domain.UserRole;
import com.example.yearend.user.domain.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UpdateMeRequest(
        @NotBlank
        @Size(max = 50)
        String name
    ) {
    }

    public record UserProfileResponse(
        UUID id,
        String email,
        String name,
        String nickname,
        UserRole role,
        UserStatus status,
        OffsetDateTime lastLoginAt
    ) {
    }
}
