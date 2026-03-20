package com.example.yearend.user.api;

import com.example.yearend.common.api.ApiResponse;
import com.example.yearend.user.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ApiResponse<AuthDtos.AuthTokenResponse> signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        return ApiResponse.success(authService.signUp(request));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthTokenResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @Operation(summary = "소셜 로그인 인가 URL 조회")
    @GetMapping("/oauth/{provider}/authorize-url")
    public ApiResponse<AuthDtos.SocialAuthorizeUrlResponse> getAuthorizeUrl(
        @PathVariable String provider,
        @RequestParam String redirectUri,
        @RequestParam(required = false) String state
    ) {
        return ApiResponse.success(authService.getSocialAuthorizeUrl(provider, redirectUri, state));
    }

    @Operation(summary = "소셜 로그인 코드 교환")
    @PostMapping("/oauth/{provider}/exchange")
    public ApiResponse<AuthDtos.AuthTokenResponse> exchangeSocialCode(
        @PathVariable String provider,
        @Valid @RequestBody AuthDtos.SocialExchangeRequest request
    ) {
        return ApiResponse.success(authService.exchangeSocialCode(provider, request));
    }
}
