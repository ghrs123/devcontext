package com.fitvision.engine.recommendation;

import com.fitvision.domain.sizechart.SizeEntry;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless service. Matches a computed BodyProfile against a list of SizeEntry rows
 * and returns the best MatchResult with a confidence score.
 *
 * No database access. All inputs are already loaded by the caller.
 */
@Service
public class SizeChartMatcher {

    private static final double OUT_OF_RANGE_CONFIDENCE_CAP = 0.5;

    /**
     * Finds the best-fitting size for the given body profile.
     *
     * @param profile computed body measurements
     * @param entries size chart rows to evaluate; may be null or empty
     * @return MatchResult — never null, returns noMatch() when no valid entries exist
     */
    public MatchResult match(BodyProfile profile, List<SizeEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return MatchResult.noMatch();
        }

        List<EntryScore> scored = entries.stream()
                .map(e -> score(profile, e))
                .filter(s -> s.availableDimensions() > 0)
                .sorted(byScoreDescThenWaistFirst())
                .toList();

        if (scored.isEmpty()) {
            return MatchResult.noMatch();
        }

        MatchResult result = toMatchResult(scored.get(0));

        if (profile.isOutOfRange()) {
            result = result.cap(OUT_OF_RANGE_CONFIDENCE_CAP);
        }

        return result;
    }

    private EntryScore score(BodyProfile profile, SizeEntry entry) {
        int matched = 0;
        int available = 0;
        boolean waistMatched = false;

        if (hasBounds(entry.getChestMin(), entry.getChestMax())) {
            available++;
            if (isWithin(profile.getEstimatedChestCm(), entry.getChestMin(), entry.getChestMax())) {
                matched++;
            }
        }

        if (hasBounds(entry.getWaistMin(), entry.getWaistMax())) {
            available++;
            if (isWithin(profile.getEstimatedWaistCm(), entry.getWaistMin(), entry.getWaistMax())) {
                matched++;
                waistMatched = true;
            }
        }

        if (hasBounds(entry.getHipMin(), entry.getHipMax())) {
            available++;
            if (isWithin(profile.getEstimatedHipCm(), entry.getHipMin(), entry.getHipMax())) {
                matched++;
            }
        }

        if (hasBounds(entry.getHeightMin(), entry.getHeightMax())) {
            available++;
            if (isWithin(profile.getHeightCm(), entry.getHeightMin(), entry.getHeightMax())) {
                matched++;
            }
        }

        double scoreValue = available > 0 ? (double) matched / available : 0.0;
        return new EntryScore(entry, scoreValue, waistMatched, available);
    }

    private boolean hasBounds(BigDecimal min, BigDecimal max) {
        return min != null && max != null;
    }

    private boolean isWithin(double value, BigDecimal min, BigDecimal max) {
        return value >= min.doubleValue() && value <= max.doubleValue();
    }

    private Comparator<EntryScore> byScoreDescThenWaistFirst() {
        return Comparator.<EntryScore>comparingDouble(EntryScore::score).reversed()
                .thenComparing(Comparator.<EntryScore>comparingInt(s -> s.waistMatched() ? 0 : 1));
    }

    private MatchResult toMatchResult(EntryScore best) {
        String size = best.entry().getSizeLabel();
        if (best.score() >= 1.0) return MatchResult.exact(size);
        if (best.score() >= 0.5) return MatchResult.partial(size, best.score());
        return MatchResult.closest(size);
    }

    private record EntryScore(SizeEntry entry, double score, boolean waistMatched, int availableDimensions) {}
}
