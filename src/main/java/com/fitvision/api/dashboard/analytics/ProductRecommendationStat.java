package com.fitvision.api.dashboard.analytics;

import java.util.UUID;

public class ProductRecommendationStat {

    private UUID productId;
    private String productName;
    private long recommendationCount;
    private double averageConfidence;

    public ProductRecommendationStat(UUID productId,
                                     String productName,
                                     long recommendationCount,
                                     double averageConfidence) {
        this.productId = productId;
        this.productName = productName;
        this.recommendationCount = recommendationCount;
        this.averageConfidence = averageConfidence;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public long getRecommendationCount() {
        return recommendationCount;
    }

    public double getAverageConfidence() {
        return averageConfidence;
    }
}
