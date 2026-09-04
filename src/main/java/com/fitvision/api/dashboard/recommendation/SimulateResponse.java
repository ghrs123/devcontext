package com.fitvision.api.dashboard.recommendation;

import com.fitvision.engine.recommendation.SimulationResult;

import java.math.BigDecimal;
import java.util.List;

/** What the simulator shows: the answer plus the reasoning behind it. */
public record SimulateResponse(
        String productName,
        String brandName,
        boolean hasSizeChart,
        String recommendedSize,
        double confidenceScore,
        String confidenceLabel,
        String quality,
        EstimatedProfile estimatedProfile,
        List<SizeRow> sizeChart
) {

    public record EstimatedProfile(double bmi, double chestCm, double waistCm, double hipCm) {}

    public record SizeRow(
            String size,
            Range chest,
            Range waist,
            Range hip,
            Range height,
            boolean recommended
    ) {}

    public record Range(BigDecimal min, BigDecimal max) {}

    public static SimulateResponse from(SimulationResult r) {
        var m = r.match();
        var p = r.profile();
        String recommended = m.getRecommendedSize();

        List<SizeRow> rows = r.entries().stream()
                .map(e -> new SizeRow(
                        e.getSizeLabel(),
                        new Range(e.getChestMin(), e.getChestMax()),
                        new Range(e.getWaistMin(), e.getWaistMax()),
                        new Range(e.getHipMin(), e.getHipMax()),
                        new Range(e.getHeightMin(), e.getHeightMax()),
                        recommended != null && recommended.equalsIgnoreCase(e.getSizeLabel())))
                .toList();

        return new SimulateResponse(
                r.productName(),
                r.brandName(),
                r.hasSizeChart(),
                recommended,
                m.getConfidenceScore(),
                confidenceLabel(m.getConfidenceScore()),
                m.getQuality().name(),
                new EstimatedProfile(p.getBmi(), p.getEstimatedChestCm(),
                        p.getEstimatedWaistCm(), p.getEstimatedHipCm()),
                rows
        );
    }

    private static String confidenceLabel(double score) {
        if (score >= 0.8) return "High";
        if (score >= 0.5) return "Medium";
        return "Low";
    }
}
