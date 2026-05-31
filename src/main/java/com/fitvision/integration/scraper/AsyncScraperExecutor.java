package com.fitvision.integration.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Executes scrape jobs asynchronously so that the admin trigger endpoint
 * returns immediately rather than blocking until the scrape completes.
 *
 * Must be a separate Spring bean from {@link ScraperService} so that the
 * {@code @Async} proxy is applied when called from {@code AdminService}.
 */
@Component
public class AsyncScraperExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncScraperExecutor.class);

    @Async
    public void execute(UUID brandId, UUID adminStoreId, ScraperService scraperService) {
        try {
            scraperService.triggerNow(brandId, adminStoreId);
        } catch (Exception ex) {
            // Job is already marked FAILED inside triggerNow/executeScrape.
            log.warn("Async scrape execution failed for brandId={}: {}", brandId, ex.getMessage());
        }
    }
}
