package com.lovablepet.global.exception;

import lombok.Getter;

/**
 * 비즈니스 로직에서 의도적으로 던지는 예외의 최상위 타입.
 * 도메인별 예외는 이 클래스를 상속해 정의한다. (예: PetNotFoundException extends BusinessException)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
