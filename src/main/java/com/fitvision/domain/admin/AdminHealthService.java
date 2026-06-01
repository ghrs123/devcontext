package com.fitvision.domain.admin;

import com.fitvision.api.admin.AdminHealthResponse;
import com.fitvision.api.admin.BrandScrapeStatusResponse;
import com.fitvision.api.admin.RecommendationStatsResponse;
import com.fitvision.api.admin.ScrapeTriggerAllResponse;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;
import com.fitvision.integration.scraper.AsyncScraperExecutor;
import com.fitvision.integration.scraper.BrandScraperRegistry;
import com.fitvision.integration.scraper.ScraperService;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.RecommendationRequestRepository;
import com.fitvision.infrastructure.persistence.ScrapeJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminHealthService {

    private static final Logger log = LoggerFactory.getLogger(AdminHealthService.class);

    private static final List<String> QUALITY_KEYS = List.of("EXACT", "PARTIAL", "CLOSEST", "NO_MATCH");
    private static final long DB_DOWN_THRESHOLD_MS = 200L;

    private final JdbcTemplate jdbcTemplate;
    private final RecommendationRequestRepository recommendationRequestRepository;
    private final ScrapeJobRepository scrapeJobRepository;
    private final BrandRepository brandRepository;
    private final BrandScraperRegistry brandScraperRegistry;
    private final ScraperService scraperService;
    private final AsyncScraperExecutor asyncScraperExecutor;

    public AdminHealthService(JdbcTemplate jdbcTemplate,
                              RecommendationRequestRepository recommendationRequestRepository,
                              ScrapeJobRepository scrapeJobRepository,
                              BrandRepository brandRepository,
                              BrandScraperRegistry brandScraperRegistry,
                              ScraperService scraperService,
                              AsyncScraperExecutor asyncScraperExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.recommendationRequestRepository = recommendationRequestRepository;
        this.scrapeJobRepository = scrapeJobRepository;
        this.brandRepository = brandRepository;
        this.brandScraperRegistry = brandScraperRegistry;
        this.scraperService = scraperService;
        this.asyncScraperExecutor = asyncScraperExecutor;
    }

    public AdminHealthResponse getHealth() {
        long dbLatency = getDatabaseLatency();
        String dbStatus = dbLatency > DB_DOWN_THRESHOLD_MS ? "DOWN" : "UP";

        Duration window24h = Duration.ofHours(24);
        LocalDateTime since24h = LocalDateTime.now().minus(window24h);
        Double avgLatency = recommendationRequestRepository.findAvgDurationSince(since24h);
        Double p95Latency = recommendationRequestRepository.findP95DurationSince(since24h);

        long running = scrapeJobRepository.countByStatusIn(
                List.of(ScrapeJobStatus.PENDING, ScrapeJobStatus.RUNNING));
        long failedLast7Days = scrapeJobRepository.countByStatusAndCreatedAtAfter(
                ScrapeJobStatus.FAILED, LocalDateTime.now().minusDays(7));

        long recommendationsLast24h = recommendationRequestRepository.countByCreatedAtAfter(since24h);
        long activeStoresLast24h = recommendationRequestRepository.countDistinctTenantsSince(since24h);
        LocalDateTime lastRecommendationAt = recommendationRequestRepository.findLastRecommendationAt();

        return new AdminHealthResponse(
                new AdminHealthResponse.DatabaseHealth(dbStatus, dbLatency),
                new AdminHealthResponse.RecommendationEngineHealth(avgLatency, p95Latency),
                new AdminHealthResponse.ScrapeJobsHealth(running, failedLast7Days),
                new AdminHealthResponse.StoreActivityHealth(
                        recommendationsLast24h,
                        activeStoresLast24h,
                        lastRecommendationAt),
                getBrandScrapeStatuses()
        );
    }

    public RecommendationStatsResponse getRecommendationStats(Duration window) {
        LocalDateTime since = LocalDateTime.now().minus(window);

        Double p50 = recommendationRequestRepository.findP50DurationSince(since);
        Double p95 = recommendationRequestRepository.findP95DurationSince(since);
        Double p99 = recommendationRequestRepository.findP99DurationSince(since);

        Map<String, Long> qualityDistribution = new LinkedHashMap<>();
        for (String quality : QUALITY_KEYS) {
            qualityDistribution.put(quality, recommendationRequestRepository.countByQualitySince(since, quality));
        }

        List<RecommendationStatsResponse.StoreRecommendationStat> topStores =
                recommendationRequestRepository.findTopStoresSince(since, Pageable.ofSize(5)).stream()
                        .map(row -> new RecommendationStatsResponse.StoreRecommendationStat(
                                (UUID) row[0],
                                (String) row[1],
                                ((Number) row[2]).longValue()))
                        .toList();

        return new RecommendationStatsResponse(p50, p95, p99, qualityDistribution, topStores);
    }

    public long getDatabaseLatency() {
        long start = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return System.currentTimeMillis() - start;
        } catch (Exception ex) {
            log.warn("Database health probe failed", ex);
            return System.currentTimeMillis() - start;
        }
    }

    public ScrapeTriggerAllResponse triggerAllScrapes(UUID adminStoreId) {
        int triggered = 0;
        int skipped = 0;

        List<Brand> globalBrands = brandRepository.findAllActive().stream()
                .filter(brand -> brand.getTenantId() == null)
                .toList();

        for (Brand brand : globalBrands) {
            if (brandScraperRegistry.findBySlug(brand.getSlug()).isEmpty()) {
                skipped++;
                continue;
            }
            if (scrapeJobRepository.existsByBrandIdAndStatus(brand.getId(), ScrapeJobStatus.PENDING)
                    || scrapeJobRepository.existsByBrandIdAndStatus(brand.getId(), ScrapeJobStatus.RUNNING)) {
                skipped++;
                continue;
            }

            scraperService.createPendingJobRecord(brand.getId());
            asyncScraperExecutor.execute(brand.getId(), adminStoreId, scraperService);
            triggered++;
        }

        log.info("Admin action: trigger-all-scrapes adminStoreId={} triggered={} skipped={}",
                adminStoreId, triggered, skipped);

        return new ScrapeTriggerAllResponse(triggered, skipped);
    }

    private List<BrandScrapeStatusResponse> getBrandScrapeStatuses() {
        return brandRepository.findAllActive().stream()
                .filter(brand -> brand.getTenantId() == null)
                .map(this::toBrandScrapeStatus)
                .toList();
    }

    private BrandScrapeStatusResponse toBrandScrapeStatus(Brand brand) {
        boolean scraperAvailable = brandScraperRegistry.findBySlug(brand.getSlug()).isPresent();
        List<ScrapeJob> jobs = scrapeJobRepository.findAllByBrandIdOrderByCreatedAtDesc(brand.getId());

        if (jobs.isEmpty()) {
            return new BrandScrapeStatusResponse(
                    brand.getId(),
                    brand.getName(),
                    null,
                    brand.getLastScrapedAt(),
                    null,
                    scraperAvailable);
        }

        ScrapeJob latest = jobs.getFirst();
        LocalDateTime timestamp = latest.getCompletedAt() != null
                ? latest.getCompletedAt()
                : latest.getCreatedAt();

        return new BrandScrapeStatusResponse(
                brand.getId(),
                brand.getName(),
                latest.getStatus(),
                timestamp,
                latest.getEntriesFound(),
                scraperAvailable);
    }
}
