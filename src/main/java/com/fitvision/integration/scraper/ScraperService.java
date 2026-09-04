package com.fitvision.integration.scraper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;
import com.fitvision.domain.sizechart.SizeChart;
import com.fitvision.domain.sizechart.SizeChartService;
import com.fitvision.domain.sizechart.SizeChartUploadResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.ScrapeJobRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;

@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private static final long MIN_REQUEST_INTERVAL_MS = 3000L;
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final SizeChartService sizeChartService;
    private final SizeChartRepository sizeChartRepository;
    private final StoreRepository storeRepository;
    private final ScrapeJobRepository scrapeJobRepository;
    private final BrandScraperRegistry scraperRegistry;

    private final Map<String, Long> lastDomainCallAt = new ConcurrentHashMap<>();

    public ScraperService(BrandRepository brandRepository,
                          ProductRepository productRepository,
                          SizeChartService sizeChartService,
                          SizeChartRepository sizeChartRepository,
                          StoreRepository storeRepository,
                          ScrapeJobRepository scrapeJobRepository,
                          BrandScraperRegistry scraperRegistry) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.sizeChartService = sizeChartService;
        this.sizeChartRepository = sizeChartRepository;
        this.storeRepository = storeRepository;
        this.scrapeJobRepository = scrapeJobRepository;
        this.scraperRegistry = scraperRegistry;
    }

    @Transactional
    public ScrapeJob triggerNow(UUID brandId, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));
        return executeScrape(brand, adminStoreId, null);
    }

    /**
     * Drives an already-created PENDING job (from {@link #createPendingJobRecord}) through
     * to completion, rather than creating a second job row.
     */
    @Transactional
    public ScrapeJob runPendingJob(UUID jobId, UUID adminStoreId) {
        ScrapeJob job = scrapeJobRepository.findById(jobId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.VALIDATION_ERROR, "Scrape job not found: " + jobId));
        Brand brand = brandRepository.findGlobalById(job.getBrandId())
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));
        return executeScrape(brand, adminStoreId, job);
    }

    /**
     * Creates a PENDING job record synchronously and returns it immediately.
     * The caller is responsible for submitting the actual scrape asynchronously.
     * Throws if a RUNNING job already exists for this brand.
     */
    @Transactional
    public ScrapeJob createPendingJobRecord(UUID brandId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        if (scrapeJobRepository.existsByBrandIdAndStatus(brandId, ScrapeJobStatus.RUNNING)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR,
                    "A scrape job is already running for brand: " + brand.getSlug());
        }

        ScrapeJob job = new ScrapeJob();
        job.setId(UUID.randomUUID());
        job.setBrandId(brandId);
        job.setStatus(ScrapeJobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setPagesScraped(0);
        job.setEntriesFound(0);
        return scrapeJobRepository.save(job);
    }

    public List<ScrapeJob> listJobs(UUID brandId) {
        return scrapeJobRepository.findAllByBrandIdOrderByCreatedAtDesc(brandId);
    }

    public Page<ScrapeJob> listJobsByStatus(ScrapeJobStatus status, Pageable pageable) {
        return scrapeJobRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
    }

    public Page<ScrapeJob> listAllJobs(Pageable pageable) {
        return scrapeJobRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public ScrapeBatchReport runScheduledScrapes() {
        long startedAtMs = System.currentTimeMillis();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Brand> brands = brandRepository.findGlobalBrandsNeedingScrape(cutoff);
        if (brands.isEmpty()) {
            return ScrapeBatchReport.empty();
        }

        UUID executorAdminStoreId = storeRepository.findFirstByRoleAndStatus(ROLE_ADMIN, STATUS_ACTIVE)
            .map(Store::getId)
                .orElse(null);

        if (executorAdminStoreId == null) {
            log.warn("Skipping scheduled scrape: no ACTIVE ADMIN store found");
            return ScrapeBatchReport.empty();
        }

        int completedJobs = 0;
        int failedJobs = 0;
        int totalEntriesFound = 0;
        int totalPagesScraped = 0;

        for (Brand brand : brands) {
            ScrapeJob job = executeScrape(brand, executorAdminStoreId, null);
            totalEntriesFound += safeInt(job.getEntriesFound());
            totalPagesScraped += safeInt(job.getPagesScraped());
            if (job.getStatus() == ScrapeJobStatus.COMPLETED) {
                completedJobs++;
            } else if (job.getStatus() == ScrapeJobStatus.FAILED) {
                failedJobs++;
            }
        }

        long durationMs = System.currentTimeMillis() - startedAtMs;
        return new ScrapeBatchReport(
                brands.size(),
                completedJobs,
                failedJobs,
                totalEntriesFound,
                totalPagesScraped,
                durationMs
        );
    }

    private ScrapeJob executeScrape(Brand brand, UUID executorStoreId, ScrapeJob existingJob) {
        ScrapeJob job = existingJob;
        if (job == null) {
            job = new ScrapeJob();
            job.setId(UUID.randomUUID());
            job.setBrandId(brand.getId());
            job.setStatus(ScrapeJobStatus.PENDING);
            job.setCreatedAt(LocalDateTime.now());
            job.setPagesScraped(0);
            job.setEntriesFound(0);
            job = scrapeJobRepository.save(job);
        }

        BrandScraper scraper = scraperRegistry.findBySlug(brand.getSlug()).orElse(null);
        if (scraper == null) {
            job.setStatus(ScrapeJobStatus.FAILED);
            job.setErrorMessage("No scraper registered for slug=" + brand.getSlug());
            job.setCompletedAt(LocalDateTime.now());
            return scrapeJobRepository.save(job);
        }

        try {
            job.setStatus(ScrapeJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now());
            scrapeJobRepository.save(job);

            throttleDomain("www." + brand.getSlug() + ".com");
            BrandScraper.ScrapePayload payload = scraper.scrape(brand);

            List<SizeEntryData> entries = payload.flattenedEntriesPreferTops();
            if (entries.isEmpty()) {
                throw new IllegalStateException("No entries scraped for brand=" + brand.getSlug());
            }
            // A scraper can "succeed" and still return junk when a site changes its markup.
            // Guard the active chart: only replace it with something that looks like a real
            // size chart. On failure this throws and drops into the catch below, leaving any
            // existing active chart untouched.
            validateScrapedEntries(entries, brand.getSlug());

            Product templateProduct = findOrCreateTemplateProductForGlobalBrand(brand, executorStoreId);
            SizeChartUploadResult upload = sizeChartService.uploadManual(executorStoreId, templateProduct.getId(), entries);

            Optional<SizeChart> activeChart = sizeChartService.getActiveSizeChart(executorStoreId, templateProduct.getId());
            activeChart.ifPresent(chart -> sizeChartRepository.updateScrapeSourceUrl(chart.getId(), payload.sourceUrl()));

            brand.setLastScrapedAt(LocalDateTime.now());
            brandRepository.save(brand);

            job.setStatus(ScrapeJobStatus.COMPLETED);
            job.setPagesScraped(payload.pagesScraped());
            job.setEntriesFound(payload.entriesFound());
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(null);
            scrapeJobRepository.save(job);

            log.info("Scrape completed: brandId={} jobId={} entries={} version={}",
                    brand.getId(), job.getId(), payload.entriesFound(), upload.getVersion());
            return job;
        } catch (Exception ex) {
            job.setStatus(ScrapeJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now());
            job.setErrorMessage(ex.getMessage());
            scrapeJobRepository.save(job);

            log.warn("Scrape failed: brandId={} jobId={} error={}", brand.getId(), job.getId(), ex.getMessage());
            return job;
        }
    }

    private static final int MIN_DISTINCT_SIZE_LABELS = 2;

    /**
     * Rejects a scraped result that is too thin to be a credible size chart, so a scraper
     * that "succeeds" against changed markup cannot overwrite a good active chart with junk.
     */
    private void validateScrapedEntries(List<SizeEntryData> entries, String slug) {
        long distinctLabels = entries.stream()
                .map(SizeEntryData::sizeLabel)
                .filter(l -> l != null && !l.isBlank())
                .map(l -> l.trim().toUpperCase())
                .distinct()
                .count();
        if (distinctLabels < MIN_DISTINCT_SIZE_LABELS) {
            throw new IllegalStateException("Scraped size chart for brand=" + slug + " has only "
                    + distinctLabels + " distinct size label(s) — refusing to replace the active chart");
        }

        long withBounds = entries.stream().filter(this::hasAnyBound).count();
        if (withBounds * 2 < entries.size()) {
            throw new IllegalStateException("Scraped size chart for brand=" + slug
                    + " has measurements on only " + withBounds + " of " + entries.size()
                    + " rows — refusing to replace the active chart");
        }
    }

    private boolean hasAnyBound(SizeEntryData e) {
        return e.chestMin() != null || e.chestMax() != null
                || e.waistMin() != null || e.waistMax() != null
                || e.hipMin() != null || e.hipMax() != null
                || e.heightMin() != null || e.heightMax() != null;
    }

    private void throttleDomain(String domain) {
        long now = System.currentTimeMillis();
        Long last = lastDomainCallAt.get(domain);
        if (last != null) {
            long elapsed = now - last;
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                long sleep = MIN_REQUEST_INTERVAL_MS - elapsed;
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        lastDomainCallAt.put(domain, System.currentTimeMillis());
    }

    private Product findOrCreateTemplateProductForGlobalBrand(Brand brand, UUID adminStoreId) {
        String externalId = templateExternalProductId(brand.getId());
        return productRepository.findAnyByExternalProductIdAndTenantId(externalId, adminStoreId)
                .map(existing -> {
                    boolean changed = false;
                    if (existing.getDeletedAt() != null) {
                        existing.setDeletedAt(null);
                        changed = true;
                    }
                    if (!brand.getId().equals(existing.getBrandId())) {
                        existing.setBrandId(brand.getId());
                        changed = true;
                    }
                    if (changed) {
                        existing.setUpdatedAt(LocalDateTime.now());
                        return productRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setId(UUID.randomUUID());
                    p.setTenantId(adminStoreId);
                    p.setBrandId(brand.getId());
                    p.setExternalProductId(externalId);
                    p.setName("Global Brand Template - " + brand.getName());
                    p.setCategory("GLOBAL");
                    p.setGenderTarget("UNISEX");
                    p.setCreatedAt(LocalDateTime.now());
                    p.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(p);
                });
    }

    private String templateExternalProductId(UUID brandId) {
        return "__global_brand__" + brandId;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
