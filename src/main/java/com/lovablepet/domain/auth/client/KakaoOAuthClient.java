package com.lovablepet.domain.auth.client;

import com.lovablepet.global.config.KakaoOAuthProperties;
import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthClient {

    private final RestClient kakaoAuthRestClient;
    private final RestClient kakaoApiRestClient;
    private final KakaoOAuthProperties properties;

    public KakaoOAuthClient(
            @Qualifier("kakaoAuthRestClient") RestClient kakaoAuthRestClient,
            @Qualifier("kakaoApiRestClient") RestClient kakaoApiRestClient,
            KakaoOAuthProperties properties
    ) {
        this.kakaoAuthRestClient = kakaoAuthRestClient;
        this.kakaoApiRestClient = kakaoApiRestClient;
        this.properties = properties;
    }

    public KakaoTokenResponse exchangeAuthorizationCode(String authorizationCode) {
        validateConfiguration();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);

        KakaoTokenResponse response = kakaoAuthRestClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "카카오 액세스 토큰을 받지 못했습니다.");
        }

        return response;
    }

    public KakaoUserResponse getUser(String accessToken) {
        KakaoUserResponse response = kakaoApiRestClient.get()
                .uri("/v2/user/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class);

        if (response == null || response.id() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "카카오 사용자 정보를 받지 못했습니다.");
        }

        return response;
    }

    private void validateConfiguration() {
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "카카오 OAuth 설정이 완료되지 않았습니다.");
        }
    }
}
