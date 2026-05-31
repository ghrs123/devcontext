package com.fitvision.api.admin;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminHealthResponse(
        DatabaseHealth database,
        RecommendationEngineHealth recommendationEngine,
        ScrapeJobsHealth scrapeJobs,
        StoreActivityHealth storeActivity,
        java.util.List<BrandScrapeStatusResponse> brandScrapes
) {
    public record DatabaseHealth(String status, long latencyMs) {}

    public record RecommendationEngineHealth(Double avgLatencyMs, Double p95LatencyMs) {}

    public record ScrapeJobsHealth(long running, long failedLast7Days) {}

    public record StoreActivityHealth(
            long recommendationsLast24h,
            long activeStoresLast24h,
            LocalDateTime lastRecommendationAt
    ) {}
}
