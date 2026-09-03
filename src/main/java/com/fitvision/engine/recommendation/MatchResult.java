package com.fitvision.engine.recommendation;

public final class MatchResult {

    public enum MatchQuality { EXACT, PARTIAL, CLOSEST, NO_MATCH }

    private final String recommendedSize;
    private final double confidenceScore;
    private final MatchQuality quality;

    private MatchResult(String recommendedSize, double confidenceScore, MatchQuality quality) {
        this.recommendedSize = recommendedSize;
        this.confidenceScore = confidenceScore;
        this.quality = quality;
    }

    public static MatchResult exact(String size) {
        return new MatchResult(size, 1.0, MatchQuality.EXACT);
    }

    public static MatchResult partial(String size, double score) {
        return new MatchResult(size, score, MatchQuality.PARTIAL);
    }

    /**
     * A best-effort match where no size fully contains the estimated measurements.
     * Inherently low-confidence — the score reflects how near the closest size is.
     */
    public static MatchResult closest(String size, double score) {
        return new MatchResult(size, score, MatchQuality.CLOSEST);
    }

    public static MatchResult noMatch() {
        return new MatchResult(null, 0.0, MatchQuality.NO_MATCH);
    }

    /** Returns a copy with confidence capped at maxScore. Quality is preserved. */
    public MatchResult cap(double maxScore) {
        if (confidenceScore <= maxScore) return this;
        return new MatchResult(recommendedSize, maxScore, quality);
    }

    public String getRecommendedSize() { return recommendedSize; }
    public double getConfidenceScore() { return confidenceScore; }
    public MatchQuality getQuality() { return quality; }
}
