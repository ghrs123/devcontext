package com.fitvision.integration.scraper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class BrandScraperRegistry {

    private final Map<String, BrandScraper> scraperBySlug;

    public BrandScraperRegistry(List<BrandScraper> scrapers) {
        this.scraperBySlug = scrapers.stream()
                .collect(Collectors.toMap(
                        scraper -> normalizeSlug(scraper.supportedSlug()),
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    public Optional<BrandScraper> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(scraperBySlug.get(normalizeSlug(slug)));
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
