package com.fitvision.api.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record GlobalBrandSizeChartVersionResponse(
        UUID id,
        int version,
        boolean active,
        String source,
        LocalDateTime createdAt
) {
}
