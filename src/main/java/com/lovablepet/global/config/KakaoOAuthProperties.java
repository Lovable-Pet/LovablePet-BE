package com.lovablepet.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kakao.oauth")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authBaseUrl,
        String apiBaseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(redirectUri);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
