package com.fitvision.api.dashboard.brand;

import jakarta.validation.constraints.NotBlank;

public record CreateBrandRequest(
        @NotBlank(message = "name is required")
        String name
) {
}
