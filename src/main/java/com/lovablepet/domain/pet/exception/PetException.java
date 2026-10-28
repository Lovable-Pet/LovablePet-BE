package com.lovablepet.domain.pet.exception;

import com.lovablepet.global.exception.BusinessException;
import com.lovablepet.global.exception.ErrorCode;

public class PetException extends BusinessException {

    public PetException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PetException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
