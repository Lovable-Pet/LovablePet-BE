package com.lovablepet.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lovablepet.global.exception.ErrorCode;

import java.util.List;

/**
 * 실패 응답의 error 부분.
 * details 는 입력값 검증 실패일 때만 항목별 오류 목록을 담고, 그 외에는 null 이다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> details
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> details) {
        List<FieldError> nonEmptyDetails = (details == null || details.isEmpty()) ? null : details;
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), nonEmptyDetails);
    }

    public record FieldError(String field, Object rejectedValue, String reason) {
    }
}
