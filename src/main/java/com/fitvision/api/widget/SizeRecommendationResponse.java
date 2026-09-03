package com.fitvision.api.widget;

import com.fitvision.engine.recommendation.MatchResult;
import com.fitvision.engine.recommendation.RecommendationOutput;

/**
 * Response body DTO for POST /api/widget/v1/size-recommendation.
 *
 * <p>Wraps the engine's {@link RecommendationOutput} into HTTP-friendly fields,
 * adding a human-readable {@code confidenceLabel} and {@code message}.
 */
public class SizeRecommendationResponse {

    /** The recommended size label (e.g. "M", "L", "XL"). Null when no match exists. */
    private final String recommendedSize;

    /** Confidence score in the range [0.0, 1.0]. */
    private final double confidenceScore;

    /** Match quality: EXACT, PARTIAL, CLOSEST, or NO_MATCH. */
    private final String quality;

    /** The name of the product for which the recommendation was made. */
    private final String productName;

    /** Whether a size chart exists for this product. */
    private final boolean hasSizeChart;

    /** Human-readable confidence label: "High", "Medium", or "Low". */
    private final String confidenceLabel;

    /** Buyer-facing message explaining the recommendation result. */
    private final String message;

    private SizeRecommendationResponse(Builder builder) {
        this.recommendedSize = builder.recommendedSize;
        this.confidenceScore = builder.confidenceScore;
        this.quality = builder.quality;
        this.productName = builder.productName;
        this.hasSizeChart = builder.hasSizeChart;
        this.confidenceLabel = builder.confidenceLabel;
        this.message = builder.message;
    }

    /**
     * Creates a {@link SizeRecommendationResponse} from the engine output,
     * computing the confidence label and message automatically.
     */
    public static SizeRecommendationResponse from(RecommendationOutput output) {
        String label = computeConfidenceLabel(output.getConfidenceScore());
        String message = computeMessage(output);
        return new Builder()
                .recommendedSize(output.getRecommendedSize())
                .confidenceScore(output.getConfidenceScore())
                .quality(output.getQuality().name())
                .productName(output.getProductName())
                .hasSizeChart(output.isHasSizeChart())
                .confidenceLabel(label)
                .message(message)
                .build();
    }

    public static SizeRecommendationResponse planLimitFallback() {
        return new Builder()
                .recommendedSize(null)
                .confidenceScore(0.0)
                .quality(MatchResult.MatchQuality.NO_MATCH.name())
                .productName(null)
                .hasSizeChart(false)
                .confidenceLabel("Low")
                .message("Size recommendations are temporarily unavailable for this store.")
                .build();
    }

    private static String computeConfidenceLabel(double score) {
        if (score >= 0.8) return "High";
        if (score >= 0.5) return "Medium";
        return "Low";
    }

    private static String computeMessage(RecommendationOutput output) {
        MatchResult.MatchQuality quality = output.getQuality();
        String size = output.getRecommendedSize();

        if (!output.isHasSizeChart()) {
            return "We don't have size data for this product yet. Please consult the brand's size guide.";
        }
        if (quality == MatchResult.MatchQuality.NO_MATCH || size == null) {
            return "We couldn't confidently match your measurements to a size. Please consult the brand's size guide.";
        }
        if (quality == MatchResult.MatchQuality.EXACT && output.getConfidenceScore() >= 0.8) {
            return "Based on your measurements, we recommend size " + size + ".";
        }
        if (quality == MatchResult.MatchQuality.PARTIAL) {
            return "We recommend size " + size + ", but please check the size guide for confirmation.";
        }
        // CLOSEST or any non-null size with lower confidence
        return "Size " + size + " is the closest match. We recommend measuring yourself before ordering.";
    }

    // --- Getters ---

    public String getRecommendedSize() {
        return recommendedSize;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public String getQuality() {
        return quality;
    }

    public String getProductName() {
        return productName;
    }

    public boolean isHasSizeChart() {
        return hasSizeChart;
    }

    public String getConfidenceLabel() {
        return confidenceLabel;
    }

    public String getMessage() {
        return message;
    }

    // --- Builder ---

    private static final class Builder {
        private String recommendedSize;
        private double confidenceScore;
        private String quality;
        private String productName;
        private boolean hasSizeChart;
        private String confidenceLabel;
        private String message;

        Builder recommendedSize(String v) { this.recommendedSize = v; return this; }
        Builder confidenceScore(double v) { this.confidenceScore = v; return this; }
        Builder quality(String v) { this.quality = v; return this; }
        Builder productName(String v) { this.productName = v; return this; }
        Builder hasSizeChart(boolean v) { this.hasSizeChart = v; return this; }
        Builder confidenceLabel(String v) { this.confidenceLabel = v; return this; }
        Builder message(String v) { this.message = v; return this; }

        SizeRecommendationResponse build() { return new SizeRecommendationResponse(this); }
    }
}
