package com.fitvision.api.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminRecommendationView(
        UUID id,
        UUID tenantId,
        String storeName,
        UUID productId,
        String productName,
        String recommendedSize,
        double confidenceScore,
        String quality,
        LocalDateTime createdAt
) {
}
