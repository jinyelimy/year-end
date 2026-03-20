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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final SocialAuthProperties socialAuthProperties;
    private final ObjectMapper objectMapper;

    private final RestClient restClient = RestClient.create();

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
        user.setNickname(request.nickname().trim());
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

    @Transactional(readOnly = true)
    public AuthDtos.SocialAuthorizeUrlResponse getSocialAuthorizeUrl(String providerName, String redirectUri, String state) {
        SocialAuthProperties.Provider provider = resolveProvider(providerName);
        validateProvider(providerName, provider);

        UriComponentsBuilder builder = UriComponentsBuilder
            .fromUriString(provider.authorizeUri())
            .queryParam("response_type", "code")
            .queryParam("client_id", provider.clientId())
            .queryParam("redirect_uri", redirectUri);

        if ("naver".equalsIgnoreCase(providerName)) {
            builder.queryParam("state", state == null || state.isBlank() ? UUID.randomUUID().toString() : state);
        }

        if (provider.scope() != null && !provider.scope().isBlank()) {
            builder.queryParam("scope", provider.scope());
        }

        return new AuthDtos.SocialAuthorizeUrlResponse(providerName.toLowerCase(Locale.ROOT), builder.build(true).toUriString());
    }

    @Transactional
    public AuthDtos.AuthTokenResponse exchangeSocialCode(String providerName, AuthDtos.SocialExchangeRequest request) {
        SocialAuthProperties.Provider provider = resolveProvider(providerName);
        validateProvider(providerName, provider);

        String providerAccessToken = requestProviderToken(providerName, provider, request);
        SocialProfile profile = requestSocialProfile(providerName, provider, providerAccessToken);

        if (profile.email() == null || profile.email().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "소셜 계정에서 이메일 정보를 가져오지 못했습니다.");
        }

        String normalizedEmail = profile.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElseGet(() -> {
            User created = new User();
            created.setEmail(normalizedEmail);
            created.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            created.setRole(UserRole.USER);
            created.setStatus(UserStatus.ACTIVE);
            return created;
        });

        user.setName(resolveText(profile.name(), normalizedEmail));
        user.setNickname(resolveText(profile.nickname(), user.getName()));
        user.setLastLoginAt(OffsetDateTime.now());

        userRepository.save(user);
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

    private SocialAuthProperties.Provider resolveProvider(String providerName) {
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "kakao" -> socialAuthProperties.kakao();
            case "naver" -> socialAuthProperties.naver();
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 소셜 로그인입니다.");
        };
    }

    private void validateProvider(String providerName, SocialAuthProperties.Provider provider) {
        if (provider == null || !provider.enabled() || provider.clientId() == null || provider.clientId().isBlank()) {
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                providerName + " 소셜 로그인 설정이 아직 완료되지 않았습니다."
            );
        }
    }

    private String requestProviderToken(
        String providerName,
        SocialAuthProperties.Provider provider,
        AuthDtos.SocialExchangeRequest request
    ) {
        MultiValueMap<String, String> payload = new LinkedMultiValueMap<>();
        payload.add("grant_type", "authorization_code");
        payload.add("client_id", provider.clientId());
        payload.add("redirect_uri", request.redirectUri());
        payload.add("code", request.code());

        if (provider.clientSecret() != null && !provider.clientSecret().isBlank()) {
            payload.add("client_secret", provider.clientSecret());
        }

        if ("naver".equalsIgnoreCase(providerName)) {
            payload.add("state", request.state());
        }

        String body = restClient.post()
            .uri(provider.tokenUri())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(payload)
            .retrieve()
            .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode accessToken = node.path("access_token");
            if (accessToken.isMissingNode() || accessToken.asText().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, providerName + " 토큰 교환에 실패했습니다.");
            }
            return accessToken.asText();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, providerName + " 토큰 응답을 처리하지 못했습니다.");
        }
    }

    private SocialProfile requestSocialProfile(String providerName, SocialAuthProperties.Provider provider, String accessToken) {
        String body = restClient.get()
            .uri(provider.userInfoUri())
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(body);
            if ("kakao".equalsIgnoreCase(providerName)) {
                JsonNode account = node.path("kakao_account");
                JsonNode profile = account.path("profile");
                return new SocialProfile(
                    account.path("email").asText(null),
                    account.path("name").asText(profile.path("nickname").asText(null)),
                    profile.path("nickname").asText(null)
                );
            }

            JsonNode response = node.path("response");
            return new SocialProfile(
                response.path("email").asText(null),
                response.path("name").asText(response.path("nickname").asText(null)),
                response.path("nickname").asText(response.path("name").asText(null))
            );
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, providerName + " 사용자 정보를 처리하지 못했습니다.");
        }
    }

    private String resolveText(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        return candidate.trim();
    }

    private record SocialProfile(
        String email,
        String name,
        String nickname
    ) {
    }
}
