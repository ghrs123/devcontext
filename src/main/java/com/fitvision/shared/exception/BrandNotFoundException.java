package com.fitvision.shared.exception;

public class BrandNotFoundException extends FitVisionException {

    public BrandNotFoundException(String message) {
        super(ErrorCode.BRAND_NOT_FOUND, message);
    }
}
