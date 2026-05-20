package com.fitvision.api.admin;

import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScrapeJobResponse(
        UUID id,
        UUID brandId,
        ScrapeJobStatus status,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Integer pagesScraped,
        Integer entriesFound,
        String errorMessage,
        LocalDateTime createdAt
) {
    public static ScrapeJobResponse from(ScrapeJob job) {
        return new ScrapeJobResponse(
                job.getId(),
                job.getBrandId(),
                job.getStatus(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getPagesScraped(),
                job.getEntriesFound(),
                job.getErrorMessage(),
                job.getCreatedAt()
        );
    }
}
