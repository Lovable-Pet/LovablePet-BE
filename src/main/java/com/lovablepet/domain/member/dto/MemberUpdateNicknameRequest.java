package com.lovablepet.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberUpdateNicknameRequest(
    @NotBlank(message = "닉네임은 필수 입력값입니다.")
    @Size(max = 50, message = "닉네임은 최대 50자까지 입력 가능합니다.")
    String nickname
) {
}
