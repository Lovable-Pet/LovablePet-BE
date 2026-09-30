package com.lovablepet.domain.member.exception;

import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;

/**
 * member 도메인(회원 정보·상태)에서 던지는 예외.
 * 응답 코드와 메시지는 ErrorCode 가 정하고, GlobalExceptionHandler 가 BusinessException 으로 받아 처리한다.
 */
public class MemberException extends BusinessException {

    public MemberException(ErrorCode errorCode) {
        super(errorCode);
    }

    public MemberException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public MemberException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
