package com.lovablepet.domain.generation.exception;

import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;

/**
 * generation 도메인(3D 생성 파이프라인 연동)에서 던지는 예외.
 * 응답 코드와 메시지는 ErrorCode 가 정하고, GlobalExceptionHandler 가 BusinessException 으로 받아 처리한다.
 */
public class GenerationException extends BusinessException {

    public GenerationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public GenerationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public GenerationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
