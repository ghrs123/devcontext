package com.fitvision.shared.exception;

public class StoreNotFoundException extends FitVisionException {

    public StoreNotFoundException(String message) {
        super(ErrorCode.STORE_NOT_FOUND, message);
    }
}
