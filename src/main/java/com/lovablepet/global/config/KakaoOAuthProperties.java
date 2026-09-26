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

    /**
     * 필수 설정 여부. Client Secret은 카카오 콘솔에서 사용하도록 켠 경우에만 필요하므로 검사하지 않는다.
     */
    public boolean isConfigured() {
        return hasText(clientId) && hasText(redirectUri);
    }

    public boolean hasClientSecret() {
        return hasText(clientSecret);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
