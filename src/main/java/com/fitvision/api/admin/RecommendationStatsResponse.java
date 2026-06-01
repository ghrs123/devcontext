package com.fitvision.api.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RecommendationStatsResponse(
        Double p50LatencyMs,
        Double p95LatencyMs,
        Double p99LatencyMs,
        Map<String, Long> qualityDistribution,
        List<StoreRecommendationStat> topStores
) {
    public record StoreRecommendationStat(
            UUID storeId,
            String storeName,
            long recommendationCount
    ) {}
}
