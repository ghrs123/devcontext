package com.fitvision.shared.response;

import com.fitvision.shared.exception.ErrorCode;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final ApiMeta meta;

    private ApiResponse(boolean success, T data, ApiError error, ApiMeta meta) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, ApiMeta.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, null), ApiMeta.now());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, String field) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, field), ApiMeta.now());
    }

    @Getter
    public static class ApiError {
        private final String code;
        private final String message;
        private final String field;

        ApiError(String code, String message, String field) {
            this.code = code;
            this.message = message;
            this.field = field;
        }
    }

    @Getter
    public static class ApiMeta {
        private final String requestId;
        private final String timestamp;

        ApiMeta(String requestId, String timestamp) {
            this.requestId = requestId;
            this.timestamp = timestamp;
        }

        static ApiMeta now() {
            return new ApiMeta(
                    MDC.get("requestId"),
                    OffsetDateTime.now().toString()
            );
        }
    }
}
