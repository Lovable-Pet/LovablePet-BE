package com.lovablepet.domain.auth.exception;

import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;

/**
 * auth 도메인(로그인·회원가입·토큰·소셜 연동)에서 던지는 예외.
 * 응답 코드와 메시지는 ErrorCode 가 정하고, GlobalExceptionHandler 가 BusinessException 으로 받아 처리한다.
 */
public class AuthException extends BusinessException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
