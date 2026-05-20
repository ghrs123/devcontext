package com.fitvision.integration.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScrapeScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScrapeScheduler.class);

    private final ScraperService scraperService;

    public ScrapeScheduler(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyGlobalBrandScrapes() {
        ScrapeBatchReport report = scraperService.runScheduledScrapes();
        log.info("Nightly scrape batch finished: totalBrands={} completed={} failed={} failureRate={} entries={} pages={} durationMs={}",
                report.totalBrands(),
                report.completedJobs(),
                report.failedJobs(),
                String.format("%.2f", report.failureRate()),
                report.totalEntriesFound(),
                report.totalPagesScraped(),
                report.durationMs());
    }
}
