package com.fitvision.engine.recommendation;

import com.fitvision.domain.sizechart.SizeEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless service. Matches a computed {@link BodyProfile} against a list of
 * {@link SizeEntry} rows and returns the best {@link MatchResult} with a confidence score.
 *
 * <p>Scoring is graded, not binary: each dimension (chest, waist, hip, height) scores 1.0
 * when the estimate falls inside the size's range and decays linearly with distance once
 * outside, reaching 0 about one size step away. Dimension scores are combined with fixed
 * weights (chest and waist dominate). This keeps a single wildly-off estimate from
 * dragging the pick to whichever size happens to bracket one dimension, and lets the
 * matcher return {@code NO_MATCH} when nothing credibly fits.
 *
 * <p>No database access. All inputs are already loaded by the caller.
 */
@Service
public class SizeChartMatcher {

    private static final double OUT_OF_RANGE_CONFIDENCE_CAP = 0.5;

    /** Distance (cm) beyond a size's bound at which a dimension's score reaches 0. */
    private static final double TOLERANCE_CHEST_CM = 6.0;
    private static final double TOLERANCE_WAIST_CM = 5.0;
    private static final double TOLERANCE_HIP_CM = 6.0;
    private static final double TOLERANCE_HEIGHT_CM = 8.0;

    private static final double WEIGHT_CHEST = 0.34;
    private static final double WEIGHT_WAIST = 0.34;
    private static final double WEIGHT_HIP = 0.20;
    private static final double WEIGHT_HEIGHT = 0.12;

    private static final double PARTIAL_THRESHOLD = 0.72;
    private static final double CLOSEST_THRESHOLD = 0.45;
    private static final double CLOSEST_CONFIDENCE_CAP = 0.6;

    /**
     * Finds the best-fitting size for the given body profile.
     *
     * @param profile computed body measurements
     * @param entries size chart rows to evaluate; may be null or empty
     * @return MatchResult — never null; {@code noMatch()} when no entry credibly fits
     */
    public MatchResult match(BodyProfile profile, List<SizeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return MatchResult.noMatch();
        }

        EntryScore best = entries.stream()
                .map(e -> score(profile, e))
                .filter(s -> s.availableDimensions() > 0)
                .min(Comparator
                        .comparingDouble((EntryScore s) -> s.overallScore()).reversed()
                        .thenComparingInt(s -> -s.contained())
                        .thenComparingInt(s -> -s.availableDimensions())
                        .thenComparingDouble(EntryScore::centerDistance))
                .orElse(null);

        if (best == null) {
            return MatchResult.noMatch();
        }

        MatchResult result = toMatchResult(best);
        if (profile.isOutOfRange()) {
            result = result.cap(OUT_OF_RANGE_CONFIDENCE_CAP);
        }
        return result;
    }

    private EntryScore score(BodyProfile profile, SizeEntry entry) {
        double weightedScore = 0.0;
        double weightSum = 0.0;
        double centerDistance = 0.0;
        int available = 0;
        int contained = 0;

        Dimension[] dimensions = {
                new Dimension(profile.getEstimatedChestCm(), entry.getChestMin(), entry.getChestMax(),
                        TOLERANCE_CHEST_CM, WEIGHT_CHEST),
                new Dimension(profile.getEstimatedWaistCm(), entry.getWaistMin(), entry.getWaistMax(),
                        TOLERANCE_WAIST_CM, WEIGHT_WAIST),
                new Dimension(profile.getEstimatedHipCm(), entry.getHipMin(), entry.getHipMax(),
                        TOLERANCE_HIP_CM, WEIGHT_HIP),
                new Dimension(profile.getHeightCm(), entry.getHeightMin(), entry.getHeightMax(),
                        TOLERANCE_HEIGHT_CM, WEIGHT_HEIGHT),
        };

        for (Dimension d : dimensions) {
            if (!d.hasBounds()) {
                continue;
            }
            available++;
            weightSum += d.weight();
            double lo = d.min().doubleValue();
            double hi = d.max().doubleValue();
            if (d.value() >= lo && d.value() <= hi) {
                contained++;
                weightedScore += d.weight();
            } else {
                double gap = d.value() < lo ? lo - d.value() : d.value() - hi;
                weightedScore += d.weight() * Math.max(0.0, 1.0 - gap / d.tolerance());
            }
            centerDistance += Math.abs(d.value() - (lo + hi) / 2.0);
        }

        double overall = weightSum > 0 ? weightedScore / weightSum : 0.0;
        return new EntryScore(entry, overall, available, contained, centerDistance);
    }

    private MatchResult toMatchResult(EntryScore best) {
        String size = best.entry().getSizeLabel();

        boolean fullyContained = best.contained() == best.availableDimensions();
        if (fullyContained && best.availableDimensions() >= 2) {
            return MatchResult.exact(size);
        }
        if (best.overallScore() >= PARTIAL_THRESHOLD) {
            return MatchResult.partial(size, round2dp(best.overallScore()));
        }
        if (best.overallScore() >= CLOSEST_THRESHOLD) {
            return MatchResult.closest(size, round2dp(Math.min(best.overallScore(), CLOSEST_CONFIDENCE_CAP)));
        }
        return MatchResult.noMatch();
    }

    private double round2dp(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Dimension(double value, BigDecimal min, BigDecimal max, double tolerance, double weight) {
        boolean hasBounds() {
            return min != null && max != null;
        }
    }

    private record EntryScore(SizeEntry entry, double overallScore, int availableDimensions,
                              int contained, double centerDistance) {}
}
