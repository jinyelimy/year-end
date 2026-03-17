package com.example.yearend.user.api;

import com.example.yearend.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignUpRequest(
        @Email
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        @NotBlank
        @Size(max = 50)
        String name
    ) {
    }

    public record LoginRequest(
        @Email
        @NotBlank
        String email,

        @NotBlank
        String password
    ) {
    }

    public record AuthTokenResponse(
        UUID userId,
        String email,
        String name,
        UserRole role,
        String accessToken,
        String refreshToken,
        long expiresInSeconds
    ) {
    }
}
