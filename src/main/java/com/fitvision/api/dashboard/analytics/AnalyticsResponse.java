package com.fitvision.api.dashboard.analytics;

import java.util.List;
import java.util.Map;

public class AnalyticsResponse {

    private long totalRecommendations;
    private long recommendationsLast30Days;
    private double averageConfidenceScore;
    private Map<String, Long> qualityDistribution;
    private List<ProductRecommendationStat> topProducts;

    public AnalyticsResponse(long totalRecommendations,
                             long recommendationsLast30Days,
                             double averageConfidenceScore,
                             Map<String, Long> qualityDistribution,
                             List<ProductRecommendationStat> topProducts) {
        this.totalRecommendations = totalRecommendations;
        this.recommendationsLast30Days = recommendationsLast30Days;
        this.averageConfidenceScore = averageConfidenceScore;
        this.qualityDistribution = qualityDistribution;
        this.topProducts = topProducts;
    }

    public long getTotalRecommendations() {
        return totalRecommendations;
    }

    public long getRecommendationsLast30Days() {
        return recommendationsLast30Days;
    }

    public double getAverageConfidenceScore() {
        return averageConfidenceScore;
    }

    public Map<String, Long> getQualityDistribution() {
        return qualityDistribution;
    }

    public List<ProductRecommendationStat> getTopProducts() {
        return topProducts;
    }
}
