package com.fitvision.api.admin;

import com.fitvision.domain.scraping.ScrapeJobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record BrandScrapeStatusResponse(
        UUID brandId,
        String brandName,
        ScrapeJobStatus status,
        LocalDateTime timestamp,
        Integer entriesFound,
        boolean scraperAvailable
) {}
