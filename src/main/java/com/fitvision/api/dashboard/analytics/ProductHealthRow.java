package com.fitvision.api.dashboard.analytics;

import java.util.List;

/**
 * One product's recommendation-quality signals, framed as "needs attention" rather than
 * "causes returns" — the numbers are engine/chart-quality proxies, not measured outcomes.
 */
public record ProductHealthRow(
        String productId,
        String productName,
        boolean hasSizeChart,
        long totalRecommendations,
        long noMatchCount,
        double noMatchRate,
        double averageConfidence,
        int attentionScore,
        List<String> reasons
) {}
