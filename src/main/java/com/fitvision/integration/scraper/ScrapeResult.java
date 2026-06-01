package com.fitvision.integration.scraper;

import java.math.BigDecimal;

/**
 * Spec-defined value object representing a single scraped size row.
 * The internal pipeline uses {@link com.fitvision.domain.sizechart.SizeEntryData}
 * via {@link BrandScraper.ScrapePayload}, but this record exists as the canonical
 * domain type described in the Phase 9 spec.
 */
public record ScrapeResult(
        String sizeLabel,
        BigDecimal chestMin,
        BigDecimal chestMax,
        BigDecimal waistMin,
        BigDecimal waistMax,
        BigDecimal hipMin,
        BigDecimal hipMax,
        BigDecimal heightMin,
        BigDecimal heightMax,
        String sourceUrl
) {
}
