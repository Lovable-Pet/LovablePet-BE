package com.lovablepet.domain.auth.dto;

/**
 * 아이디 중복 확인 결과. available=true 면 사용할 수 있는 아이디.
 */
public record UsernameCheckResponse(
    boolean available
) {
}
