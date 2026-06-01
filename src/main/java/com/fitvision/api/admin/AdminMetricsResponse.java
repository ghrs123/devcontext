package com.fitvision.api.admin;

import java.util.List;
import java.util.Map;

public record AdminMetricsResponse(
        long totalStores,
        long activeStores,
        long totalRecommendations,
        long recommendationsLast30Days,
        double averageConfidenceScore,
        Map<String, Long> qualityDistribution,
        List<BrandRecommendationStat> topBrands
) {
}
