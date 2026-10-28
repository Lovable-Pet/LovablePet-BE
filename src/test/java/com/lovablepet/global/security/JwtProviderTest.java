package com.lovablepet.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String JWT_SECRET = "v/Z5Y1B/L8xXbK2G7zUeT9aR3vC5bN8xQ2W1yM4kZ6U=";

    private final JwtProvider jwtProvider = new JwtProvider(JWT_SECRET, 1_800_000L, 1_209_600_000L);

    @Test
    @DisplayName("액세스 토큰에 넣은 회원 ID와 역할을 그대로 읽는다")
    void roundTrip() {
        String token = jwtProvider.createAccessToken(5L, "ADMIN");

        JwtProvider.AccessTokenClaims claims = jwtProvider.parseAccessToken(token);

        assertThat(claims.memberId()).isEqualTo(5L);
        assertThat(claims.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("역할이 없는 토큰(역할 도입 전에 발급된 토큰)은 USER로 읽는다")
    void tokenWithoutRole_isUser() {
        String legacyToken = Jwts.builder()
                .subject("5")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)))
                .compact();

        assertThat(jwtProvider.parseAccessToken(legacyToken).role()).isEqualTo("USER");
    }
}
