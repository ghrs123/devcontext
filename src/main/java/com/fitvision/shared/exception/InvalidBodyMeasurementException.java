package com.fitvision.shared.exception;

public class InvalidBodyMeasurementException extends FitVisionException {

    public InvalidBodyMeasurementException(String message) {
        super(ErrorCode.INVALID_BODY_MEASUREMENTS, message);
    }
}
