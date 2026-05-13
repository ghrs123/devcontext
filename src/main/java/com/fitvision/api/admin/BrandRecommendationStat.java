package com.fitvision.api.admin;

import java.util.UUID;

public record BrandRecommendationStat(
        UUID brandId,
        String brandName,
        long recommendationCount,
        double averageConfidence
) {
}
