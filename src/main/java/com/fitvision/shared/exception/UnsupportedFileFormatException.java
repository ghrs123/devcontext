package com.fitvision.shared.exception;

/**
 * Thrown when an uploaded file has an unsupported format (not CSV or Excel).
 */
public class UnsupportedFileFormatException extends FitVisionException {

    public UnsupportedFileFormatException(String message) {
        super(ErrorCode.UNSUPPORTED_FILE_FORMAT, message);
    }
}
