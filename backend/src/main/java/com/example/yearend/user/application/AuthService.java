package com.example.yearend.user.application;

import com.example.yearend.common.exception.BusinessException;
import com.example.yearend.common.exception.ErrorCode;
import com.example.yearend.security.JwtProperties;
import com.example.yearend.security.JwtTokenProvider;
import com.example.yearend.user.api.AuthDtos;
import com.example.yearend.user.domain.User;
import com.example.yearend.user.domain.UserRole;
import com.example.yearend.user.domain.UserStatus;
import com.example.yearend.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public AuthDtos.AuthTokenResponse signUp(AuthDtos.SignUpRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setNickname(request.name().trim());
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLoginAt(OffsetDateTime.now());

        userRepository.save(user);
        return issueToken(user);
    }

    @Transactional
    public AuthDtos.AuthTokenResponse login(AuthDtos.LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            user.setNickname(user.getName());
        }
        user.setLastLoginAt(OffsetDateTime.now());

        return issueToken(user);
    }

    private AuthDtos.AuthTokenResponse issueToken(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail(), Map.of(
            "role", user.getRole().name(),
            "name", user.getName()
        ));
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        return new AuthDtos.AuthTokenResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            resolveNickname(user),
            user.getRole(),
            accessToken,
            refreshToken,
            jwtProperties.accessTokenExpirationSeconds()
        );
    }

    private String resolveNickname(User user) {
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            return user.getName();
        }
        return user.getNickname();
    }
}
