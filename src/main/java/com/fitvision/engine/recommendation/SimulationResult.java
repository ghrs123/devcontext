package com.fitvision.engine.recommendation;

import com.fitvision.domain.sizechart.SizeEntry;

import java.util.List;

/**
 * The full result of a dry-run recommendation ({@link RecommendationEngine#simulate}).
 *
 * <p>Unlike {@link RecommendationOutput}, this carries the intermediate reasoning — the
 * estimated body profile and the size-chart rows that were evaluated — so the store owner
 * can see <em>why</em> a size was picked. It is never persisted and never counts against
 * the plan limit.
 */
public record SimulationResult(
        MatchResult match,
        BodyProfile profile,
        List<SizeEntry> entries,
        String productName,
        String brandName,
        boolean hasSizeChart
) {}
