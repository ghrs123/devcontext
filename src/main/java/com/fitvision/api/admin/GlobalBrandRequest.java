package com.fitvision.api.admin;

import jakarta.validation.constraints.NotBlank;

public record GlobalBrandRequest(
        @NotBlank(message = "name is required")
        String name
) {
}
