package com.fitvision.integration.scraper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScrapeScheduler {

    private final ScraperService scraperService;

    public ScrapeScheduler(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyGlobalBrandScrapes() {
        scraperService.runScheduledScrapes();
    }
}
