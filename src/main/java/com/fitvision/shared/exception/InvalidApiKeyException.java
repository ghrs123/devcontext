package com.fitvision.shared.exception;

public class InvalidApiKeyException extends FitVisionException {

    public InvalidApiKeyException(String message) {
        super(ErrorCode.INVALID_API_KEY, message);
    }
}
