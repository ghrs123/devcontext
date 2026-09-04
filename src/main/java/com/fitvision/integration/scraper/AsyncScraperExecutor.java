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
    public void execute(UUID jobId, UUID adminStoreId, ScraperService scraperService) {
        try {
            scraperService.runPendingJob(jobId, adminStoreId);
        } catch (Exception ex) {
            // Job is already marked FAILED inside runPendingJob/executeScrape.
            log.warn("Async scrape execution failed for jobId={}: {}", jobId, ex.getMessage());
        }
    }
}
