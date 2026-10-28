package com.lovablepet.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 아이디 중복 확인 결과. available=true 면 사용할 수 있는 아이디.
 */
@Schema(description = "아이디 중복 확인 결과")
public record UsernameCheckResponse(
    @Schema(description = "true 면 사용할 수 있는 아이디", example = "true")
    boolean available
) {
}
