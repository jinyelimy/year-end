package com.example.yearend.user.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.oauth")
public record SocialAuthProperties(
    Provider kakao,
    Provider naver
) {

    public record Provider(
        boolean enabled,
        String clientId,
        String clientSecret,
        String authorizeUri,
        String tokenUri,
        String userInfoUri,
        String scope
    ) {
    }
}
