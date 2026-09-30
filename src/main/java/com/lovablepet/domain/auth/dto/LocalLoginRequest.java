package com.lovablepet.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LocalLoginRequest(
    @NotBlank(message = "아이디는 필수입니다.")
    String username,

    @NotBlank(message = "비밀번호는 필수입니다.")
    String password
) {

    // 아이디 앞뒤 공백을 제거한다. (비밀번호는 공백도 값의 일부이므로 건드리지 않음)
    public LocalLoginRequest {
        username = username == null ? null : username.strip();
    }
}
