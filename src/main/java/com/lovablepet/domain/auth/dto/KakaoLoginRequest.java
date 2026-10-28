package com.lovablepet.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record KakaoLoginRequest(
    @Schema(description = "카카오 로그인 후 redirect_uri 로 돌아올 때 받은 인가 코드 (code 쿼리 파라미터 값)")
    @NotBlank(message = "카카오 인증 코드는 필수입니다.")
    String authCode
) {
}
