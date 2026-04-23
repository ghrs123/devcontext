package com.fitvision.shared.exception;

import lombok.Getter;

@Getter
public class FitVisionException extends RuntimeException {

    private final ErrorCode errorCode;

    public FitVisionException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
