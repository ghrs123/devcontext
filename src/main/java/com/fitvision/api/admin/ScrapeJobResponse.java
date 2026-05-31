package com.fitvision.api.admin;

import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record ScrapeJobResponse(
        UUID id,
        UUID brandId,
        String brandName,
        ScrapeJobStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer pagesScraped,
        Integer entriesFound,
        String errorMessage,
        LocalDateTime createdAt,
        Long durationSeconds,
        boolean isStale
) {
    private static final long STALE_DAYS = 30L;

    public static ScrapeJobResponse from(ScrapeJob job, String brandName, LocalDateTime lastScrapedAt) {
        Long duration = null;
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            duration = ChronoUnit.SECONDS.between(job.getStartedAt(), job.getCompletedAt());
        }
        boolean stale = lastScrapedAt == null ||
                lastScrapedAt.isBefore(LocalDateTime.now().minusDays(STALE_DAYS));

        return new ScrapeJobResponse(
                job.getId(),
                job.getBrandId(),
                brandName,
                job.getStatus(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getPagesScraped(),
                job.getEntriesFound(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                duration,
                stale
        );
    }

    // Backwards-compatible factory used where brand context is unavailable.
    public static ScrapeJobResponse from(ScrapeJob job) {
        return from(job, null, null);
    }
}
