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

    @Scheduled(cron = "0 0 2 * * MON")
    public void runWeeklyGlobalBrandScrapes() {
        ScrapeBatchReport report = scraperService.runScheduledScrapes();
        log.info("Weekly scrape batch finished: totalBrands={} completed={} failed={} entries={} pages={} durationMs={}",
                report.totalBrands(),
                report.completedJobs(),
                report.failedJobs(),
                report.totalEntriesFound(),
                report.totalPagesScraped(),
                report.durationMs());
    }
}
