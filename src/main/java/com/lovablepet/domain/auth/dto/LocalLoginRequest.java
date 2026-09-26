package com.lovablepet.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LocalLoginRequest(
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {

    // 검증(@Email)보다 먼저 실행되어, 이메일 앞뒤 공백을 제거한다. (비밀번호는 공백도 값의 일부이므로 건드리지 않음)
    public LocalLoginRequest {
        email = email == null ? null : email.strip();
    }
}
