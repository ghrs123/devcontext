package com.fitvision.integration.scraper;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.sizechart.SizeEntryData;

import java.util.List;
import java.util.Map;

public interface BrandScraper {

    String supportedSlug();

    ScrapePayload scrape(Brand brand) throws Exception;

    default boolean supports(Brand brand) {
        return brand != null && brand.getSlug() != null && supportedSlug().equalsIgnoreCase(brand.getSlug());
    }

    record ScrapePayload(
            Map<String, List<SizeEntryData>> entriesByCategory,
            String sourceUrl,
            int pagesScraped,
            List<String> warnings
    ) {
        public int entriesFound() {
            if (entriesByCategory == null || entriesByCategory.isEmpty()) {
                return 0;
            }
            return entriesByCategory.values().stream().mapToInt(List::size).sum();
        }

        public List<SizeEntryData> flattenedEntriesPreferTops() {
            if (entriesByCategory == null || entriesByCategory.isEmpty()) {
                return List.of();
            }
            List<SizeEntryData> tops = entriesByCategory.getOrDefault("tops", List.of());
            if (!tops.isEmpty()) {
                return tops;
            }
            return entriesByCategory.values().stream().flatMap(List::stream).toList();
        }
    }
}
