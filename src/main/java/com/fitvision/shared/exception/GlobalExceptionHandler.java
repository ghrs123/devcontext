package com.fitvision.shared.exception;

import com.fitvision.shared.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(FitVisionException.class)
    public ResponseEntity<ApiResponse<Void>> handleFitVisionException(FitVisionException ex) {
        log.error("FitVisionException [requestId={}] code={} message={}",
                MDC.get("requestId"), ex.getErrorCode(), ex.getMessage());
        HttpStatus status = resolveStatus(ex.getErrorCode());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation error [requestId={}]", MDC.get("requestId"));
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null ? fieldError.getDefaultMessage() : "Validation failed";
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, message, field));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("File upload rejected — size exceeded [requestId={}]", MDC.get("requestId"));
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR,
                        "Uploaded file exceeds the maximum allowed size of 2 MB."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        log.warn("Multipart request error [requestId={}]: {}", MDC.get("requestId"), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR,
                        "Invalid multipart request: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error [requestId={}]", MDC.get("requestId"), ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }

    private HttpStatus resolveStatus(ErrorCode code) {
        return switch (code) {
            case SIZE_CHART_NOT_FOUND, PRODUCT_NOT_FOUND, STORE_NOT_FOUND, BRAND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_API_KEY, INVALID_SECRET_KEY, UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case INVALID_BODY_MEASUREMENTS, VALIDATION_ERROR,
                 UNSUPPORTED_FILE_FORMAT, SIZE_CHART_PARSE_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

