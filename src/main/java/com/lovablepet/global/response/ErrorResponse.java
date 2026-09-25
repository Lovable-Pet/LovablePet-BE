package com.lovablepet.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lovablepet.global.exception.ErrorCode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors);
    }

    public record FieldError(String field, Object rejectedValue, String reason) {
    }
}
