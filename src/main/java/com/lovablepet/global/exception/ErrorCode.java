package com.lovablepet.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. 도메인별 에러 코드는 도메인 추가 시 여기에 이어서 정의한다.
 * (코드 접두어 규칙: COMMON_, PET_, GENERATION_, MODEL_, MATCH_, SURVEY_, STORAGE_)
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 입력값입니다."),
    INVALID_TYPE(HttpStatus.BAD_REQUEST, "COMMON_400_TYPE", "요청 값의 타입이 올바르지 않습니다."),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "COMMON_400_PARAM", "필수 요청 파라미터가 누락되었습니다."),
    MALFORMED_BODY(HttpStatus.BAD_REQUEST, "COMMON_400_BODY", "요청 본문을 읽을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "요청이 현재 리소스 상태와 충돌합니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "COMMON_413", "업로드 가능한 파일 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_415", "지원하지 않는 Content-Type입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    // External (Python AI 서버 등)
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "EXTERNAL_502", "외부 서버 호출 중 오류가 발생했습니다."),
    EXTERNAL_API_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "EXTERNAL_504", "외부 서버 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
