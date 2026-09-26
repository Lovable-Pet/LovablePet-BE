package com.lovablepet.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpMillis;
    private final long refreshTokenExpMillis;

    public JwtProvider(
        @Value("${app.auth.jwt-secret}") String secretKey,
        @Value("${app.auth.access-token-expiration}") long accessTokenExpMillis,
        @Value("${app.auth.refresh-token-expiration}") long refreshTokenExpMillis) {

        // Base64로 인코딩된 시크릿 키를 디코딩하여 안전한 키 객체로 변환
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);

        this.accessTokenExpMillis = accessTokenExpMillis;
        this.refreshTokenExpMillis = refreshTokenExpMillis;
    }

    /**
     * 유저 ID를 담은 엑세스 토큰(JWT) 발급
     */
    public String createAccessToken(Long memberId) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessTokenExpMillis);

        return Jwts.builder()
            .subject(String.valueOf(memberId)) // 토큰의 주인을 memberId로 설정
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact();
    }

    /**
     * 서명과 만료 시각이 검증된 access token에서 회원 ID를 읽는다.
     */
    public Long getMemberId(String accessToken) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(accessToken)
            .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    /**
     * 리프레시 토큰용 고엔트로피 난수 발급 (JWT가 아닌 순수 난수로 발급하여 가볍게 유지)
     */
    public String createRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 현재 시간 기준으로 리프레시 토큰의 만료 일시(LocalDateTime) 계산
     */
    public LocalDateTime getRefreshExpiration() {
        return LocalDateTime.now().plus(Duration.ofMillis(refreshTokenExpMillis));
    }
}
