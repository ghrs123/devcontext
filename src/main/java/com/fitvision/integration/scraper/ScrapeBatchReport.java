package com.fitvision.integration.scraper;

public record ScrapeBatchReport(
        int totalBrands,
        int completedJobs,
        int failedJobs,
        int totalEntriesFound,
        int totalPagesScraped,
        long durationMs
) {
    public static ScrapeBatchReport empty() {
        return new ScrapeBatchReport(0, 0, 0, 0, 0, 0L);
    }

    public double failureRate() {
        if (totalBrands == 0) {
            return 0.0;
        }
        return (double) failedJobs / (double) totalBrands;
    }
}
