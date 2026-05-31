package com.fitvision.shared.exception;

import com.fitvision.domain.billing.PlanLimitException;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.sentry.Sentry;
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

import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handlePlanLimitException(PlanLimitException ex) {
        log.warn("Plan limit reached [requestId={}]: {}", MDC.get("requestId"), ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

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
        captureUnexpectedException(ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }

    private void captureUnexpectedException(Exception ex) {
        Sentry.configureScope(scope -> {
            String requestId = MDC.get("requestId");
            if (requestId != null) {
                scope.setTag("requestId", requestId);
            }
            String tenantId = MDC.get(TenantContext.MDC_TENANT_ID_KEY);
            if (tenantId == null) {
                UUID ctxTenantId = TenantContext.get();
                if (ctxTenantId != null) {
                    tenantId = ctxTenantId.toString();
                }
            }
            if (tenantId != null) {
                scope.setTag("tenantId", tenantId);
            }
        });
        Sentry.captureException(ex);
    }

    private HttpStatus resolveStatus(ErrorCode code) {
        return switch (code) {
            case SIZE_CHART_NOT_FOUND, PRODUCT_NOT_FOUND, STORE_NOT_FOUND, BRAND_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_API_KEY, INVALID_SECRET_KEY, INVALID_CREDENTIALS, UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case STORE_ALREADY_EXISTS, ADMIN_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case INVALID_BODY_MEASUREMENTS, VALIDATION_ERROR,
                 UNSUPPORTED_FILE_FORMAT, SIZE_CHART_PARSE_ERROR -> HttpStatus.BAD_REQUEST;
            case PLAN_LIMIT_REACHED -> HttpStatus.PAYMENT_REQUIRED;
            case STRIPE_ERROR -> HttpStatus.BAD_GATEWAY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

