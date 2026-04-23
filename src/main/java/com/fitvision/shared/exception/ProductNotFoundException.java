package com.fitvision.shared.exception;

public class ProductNotFoundException extends FitVisionException {

    public ProductNotFoundException(String message) {
        super(ErrorCode.PRODUCT_NOT_FOUND, message);
    }
}
