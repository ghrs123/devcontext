package com.fitvision.api.admin;

import jakarta.validation.constraints.NotBlank;

public record UpdateStoreStatusRequest(
        @NotBlank(message = "status is required")
        String status
) {
}
