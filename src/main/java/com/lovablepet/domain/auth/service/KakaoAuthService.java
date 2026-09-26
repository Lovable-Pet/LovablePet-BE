package com.lovablepet.domain.auth.service;

import com.lovablepet.domain.auth.client.KakaoOAuthClient;
import com.lovablepet.domain.auth.client.KakaoTokenResponse;
import com.lovablepet.domain.auth.client.KakaoUserResponse;
import com.lovablepet.domain.auth.dto.AuthTokenResponse;
import com.lovablepet.domain.auth.entity.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 카카오 로그인.
 * 카카오 API 호출은 트랜잭션 밖에서 수행해, 외부 응답을 기다리는 동안 DB 커넥션을 붙잡지 않는다.
 * DB 작업은 AuthService#loginWithOAuth 에서 하나의 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private static final int MAX_NICKNAME_LENGTH = 50;

    private final KakaoOAuthClient kakaoOAuthClient;
    private final AuthService authService;

    public AuthTokenResponse login(String authorizationCode) {
        KakaoTokenResponse token = kakaoOAuthClient.exchangeAuthorizationCode(authorizationCode);
        KakaoUserResponse kakaoUser = kakaoOAuthClient.getUser(token.accessToken());

        return authService.loginWithOAuth(
                OAuthProvider.KAKAO,
                kakaoUser.id().toString(),
                resolveNickname(kakaoUser)
        );
    }

    private String resolveNickname(KakaoUserResponse kakaoUser) {
        String nickname = kakaoUser.nickname();
        if (nickname == null || nickname.isBlank() || nickname.trim().length() > MAX_NICKNAME_LENGTH) {
            return "kakao-" + kakaoUser.id();
        }
        return nickname.trim();
    }
}
