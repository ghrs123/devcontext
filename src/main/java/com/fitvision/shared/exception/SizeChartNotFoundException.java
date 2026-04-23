package com.fitvision.shared.exception;

public class SizeChartNotFoundException extends FitVisionException {

    public SizeChartNotFoundException(String message) {
        super(ErrorCode.SIZE_CHART_NOT_FOUND, message);
    }
}
