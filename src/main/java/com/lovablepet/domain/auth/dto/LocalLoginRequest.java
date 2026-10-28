package com.lovablepet.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "아이디 로그인 요청")
public record LocalLoginRequest(
    @Schema(description = "가입한 아이디 (대소문자 구분 안 함)", example = "lovable_01")
    @NotBlank(message = "아이디는 필수입니다.")
    String username,

    @Schema(description = "비밀번호", example = "password1234")
    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {

    // 아이디 앞뒤 공백을 제거한다. (비밀번호는 공백도 값의 일부이므로 건드리지 않음)
    public LocalLoginRequest {
        username = username == null ? null : username.strip();
    }
}
