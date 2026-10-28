package com.lovablepet.domain.auth.dto;

public record AuthTokenResponse(
    String accessToken,
    String refreshToken
) {
}
