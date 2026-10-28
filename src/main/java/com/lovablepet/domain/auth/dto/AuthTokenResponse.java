package com.lovablepet.domain.auth.dto;

import com.lovablepet.domain.member.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "발급된 토큰")
public record AuthTokenResponse(
    @Schema(description = "API 호출용 JWT. `Authorization: Bearer {accessToken}` 헤더로 보낸다 (기본 30분)")
    String accessToken,

    @Schema(description = "액세스 토큰 재발급용 (기본 14일). 재발급하면 새 값으로 바뀌므로 응답의 값으로 바꿔 저장한다")
    String refreshToken,

    @Schema(description = "회원 역할. ADMIN 이면 관리자 기능(AR 만들기 등)을 보여 준다", example = "USER")
    MemberRole role
) {
}
