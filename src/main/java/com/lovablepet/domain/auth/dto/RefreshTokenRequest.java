package com.lovablepet.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "리프레시 토큰 요청 (재발급·로그아웃)")
public record RefreshTokenRequest(
    @Schema(description = "로그인·회원가입·재발급 응답으로 받은 refreshToken")
    @NotBlank(message = "리프레시 토큰은 필수입니다.")
    String refreshToken
) {
}
