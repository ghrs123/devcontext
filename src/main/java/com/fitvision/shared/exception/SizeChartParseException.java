package com.fitvision.shared.exception;

/**
 * Thrown when a size chart file cannot be parsed or produces no usable entries.
 */
public class SizeChartParseException extends FitVisionException {

    public SizeChartParseException(String message) {
        super(ErrorCode.SIZE_CHART_PARSE_ERROR, message);
    }
}
