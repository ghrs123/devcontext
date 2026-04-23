package com.fitvision.engine.recommendation;

/**
 * Output DTO for a completed recommendation.
 * Returned by {@link RecommendationEngine#recommend(RecommendationInput)}.
 */
public final class RecommendationOutput {

    private final String recommendedSize;
    private final double confidenceScore;
    private final MatchResult.MatchQuality quality;
    private final String productName;
    private final String brandName;
    private final boolean hasSizeChart;
    /** Populated in a future phase; null when not yet available. */
    private final String fallbackUrl;

    private RecommendationOutput(Builder builder) {
        this.recommendedSize = builder.recommendedSize;
        this.confidenceScore = builder.confidenceScore;
        this.quality = builder.quality;
        this.productName = builder.productName;
        this.brandName = builder.brandName;
        this.hasSizeChart = builder.hasSizeChart;
        this.fallbackUrl = builder.fallbackUrl;
    }

    public String getRecommendedSize() { return recommendedSize; }
    public double getConfidenceScore() { return confidenceScore; }
    public MatchResult.MatchQuality getQuality() { return quality; }
    public String getProductName() { return productName; }
    public String getBrandName() { return brandName; }
    public boolean isHasSizeChart() { return hasSizeChart; }
    public String getFallbackUrl() { return fallbackUrl; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String recommendedSize;
        private double confidenceScore;
        private MatchResult.MatchQuality quality;
        private String productName;
        private String brandName;
        private boolean hasSizeChart;
        private String fallbackUrl;

        public Builder recommendedSize(String v) { this.recommendedSize = v; return this; }
        public Builder confidenceScore(double v) { this.confidenceScore = v; return this; }
        public Builder quality(MatchResult.MatchQuality v) { this.quality = v; return this; }
        public Builder productName(String v) { this.productName = v; return this; }
        public Builder brandName(String v) { this.brandName = v; return this; }
        public Builder hasSizeChart(boolean v) { this.hasSizeChart = v; return this; }
        public Builder fallbackUrl(String v) { this.fallbackUrl = v; return this; }

        public RecommendationOutput build() { return new RecommendationOutput(this); }
    }
}
