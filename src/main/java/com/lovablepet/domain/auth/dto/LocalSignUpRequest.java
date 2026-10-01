package com.lovablepet.domain.auth.dto;

import com.lovablepet.domain.auth.entity.LocalCredential;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LocalSignUpRequest(
    // 로그인 아이디 (대문자로 입력해도 소문자로 저장)
    @NotBlank(message = "아이디는 필수입니다.")
    @Pattern(regexp = LocalCredential.USERNAME_REGEX, message = LocalCredential.USERNAME_MESSAGE)
    String username,

    // 연락·계정 찾기용 이메일 (로그인에는 사용하지 않음)
    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자를 초과할 수 없습니다.")
    String email,

    // bcrypt는 72바이트 이후를 무시하므로 최대 길이를 제한한다.
    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
    String password,

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자를 초과할 수 없습니다.")
    String nickname
) {

    // 검증(@Pattern, @Email 등)보다 먼저 실행되어, 복사·붙여넣기로 섞인 앞뒤 공백을 제거한다. (비밀번호는 제외)
    public LocalSignUpRequest {
        username = strip(username);
        email = strip(email);
        nickname = strip(nickname);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
