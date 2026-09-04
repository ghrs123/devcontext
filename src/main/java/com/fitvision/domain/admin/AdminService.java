package com.fitvision.domain.admin;

import com.fitvision.api.admin.AdminMetricsResponse;
import com.fitvision.api.admin.AdminRecommendationView;
import com.fitvision.api.admin.BrandRecommendationStat;
import com.fitvision.api.admin.GlobalBrandSizeChartVersionResponse;
import com.fitvision.api.admin.ScrapeJobResponse;
import com.fitvision.api.admin.StoreAdminView;
import com.fitvision.api.dashboard.brand.BrandResponse;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.recommendation.RecommendationRequest;
import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeChartService;
import com.fitvision.domain.sizechart.SizeChartUploadResult;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.RecommendationRequestRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.domain.scraping.ScrapeJobStatus;
import com.fitvision.integration.scraper.AsyncScraperExecutor;
import com.fitvision.integration.scraper.ScraperService;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private static final List<String> QUALITY_KEYS = List.of("EXACT", "PARTIAL", "CLOSEST", "NO_MATCH");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String STATUS_ALL = "ALL";

    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final RecommendationRequestRepository recommendationRequestRepository;
    private final SizeChartRepository sizeChartRepository;
    private final SizeChartService sizeChartService;
    private final ScraperService scraperService;
    private final AsyncScraperExecutor asyncScraperExecutor;

    public AdminService(StoreRepository storeRepository,
                        ProductRepository productRepository,
                        BrandRepository brandRepository,
                        RecommendationRequestRepository recommendationRequestRepository,
                        SizeChartRepository sizeChartRepository,
                        SizeChartService sizeChartService,
                        ScraperService scraperService,
                        AsyncScraperExecutor asyncScraperExecutor) {
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.recommendationRequestRepository = recommendationRequestRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.sizeChartService = sizeChartService;
        this.scraperService = scraperService;
        this.asyncScraperExecutor = asyncScraperExecutor;
    }

    public AdminMetricsResponse getMetrics() {
        long totalStores = storeRepository.count();
        long activeStores = storeRepository.countByStatus(STATUS_ACTIVE);
        long totalRecommendations = recommendationRequestRepository.count();
        long recommendationsLast30Days = recommendationRequestRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(30));
        double averageConfidenceScore = Optional.ofNullable(recommendationRequestRepository.findAverageConfidenceGlobal()).orElse(0.0);

        Map<String, Long> qualityDistribution = new LinkedHashMap<>();
        for (String quality : QUALITY_KEYS) {
            qualityDistribution.put(quality, recommendationRequestRepository.countByQualityGlobal(quality));
        }

        List<BrandRecommendationStat> topBrands = recommendationRequestRepository.findTopBrands(Pageable.ofSize(5)).stream()
                .map(this::toBrandStat)
                .toList();

        return new AdminMetricsResponse(
                totalStores,
                activeStores,
                totalRecommendations,
                recommendationsLast30Days,
                averageConfidenceScore,
                qualityDistribution,
                topBrands
        );
    }

    public Page<StoreAdminView> getStores(String status, String search, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedSearch = search == null ? "" : search.trim();

        return storeRepository.findAdminStores(normalizedStatus, normalizedSearch, pageable)
                .map(this::toStoreAdminView);
    }

    public StoreAdminView getStore(UUID storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.STORE_NOT_FOUND, "Store not found"));
        return toStoreAdminView(store);
    }

    @Transactional
    public StoreAdminView updateStoreStatus(UUID storeId, String status, UUID adminStoreId) {
        String normalizedStatus = normalizeStatus(status);
        if (STATUS_ALL.equals(normalizedStatus)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Status must be ACTIVE or INACTIVE");
        }

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.STORE_NOT_FOUND, "Store not found"));

        store.setStatus(normalizedStatus);
        store.setUpdatedAt(LocalDateTime.now());
        Store saved = storeRepository.save(store);

        log.info("Admin action: update-store-status adminStoreId={} targetStoreId={} status={}",
                adminStoreId, storeId, normalizedStatus);

        return toStoreAdminView(saved);
    }

    public List<BrandResponse> getBrands() {
        return brandRepository.findAllActive().stream()
                .map(BrandResponse::from)
                .toList();
    }

    @Transactional
    public BrandResponse createGlobalBrand(String name, UUID adminStoreId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand name is required");
        }

        String slug = slugify(trimmed);
        if (slug.isBlank()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand name must contain letters or numbers");
        }

        if (brandRepository.findBySlug(slug).isPresent()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand slug already exists");
        }

        Brand brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setTenantId(null);
        brand.setName(trimmed);
        brand.setSlug(slug);
        brand.setSource("fitvision_managed");
        brand.setCreatedAt(LocalDateTime.now());

        Brand saved = brandRepository.save(brand);

        log.info("Admin action: create-global-brand adminStoreId={} brandId={} slug={}", adminStoreId, saved.getId(), slug);

        return BrandResponse.from(saved);
    }

    @Transactional
    public BrandResponse updateGlobalBrand(UUID brandId, String name, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand name is required");
        }

        String slug = slugify(trimmed);
        Optional<Brand> conflict = brandRepository.findBySlug(slug);
        if (conflict.isPresent() && !conflict.get().getId().equals(brand.getId())) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand slug already exists");
        }

        brand.setName(trimmed);
        brand.setSlug(slug);
        Brand saved = brandRepository.save(brand);

        log.info("Admin action: update-global-brand adminStoreId={} brandId={} slug={}", adminStoreId, saved.getId(), slug);

        return BrandResponse.from(saved);
    }

    @Transactional
    public void deleteGlobalBrand(UUID brandId, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        brand.setDeletedAt(LocalDateTime.now());
        brand.setSlug(brand.getSlug() + "-deleted-" + brand.getId().toString().substring(0, 8));
        brandRepository.save(brand);

        productRepository.clearBrandAssociationGlobal(brandId, LocalDateTime.now());

        log.info("Admin action: delete-global-brand adminStoreId={} brandId={}", adminStoreId, brandId);
    }

    @Transactional
    public SizeChartUploadResult uploadGlobalBrandSizeChart(UUID brandId,
                                                            ParseResult parseResult,
                                                            String source,
                                                            UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        Product templateProduct = findOrCreateTemplateProductForGlobalBrand(brand, adminStoreId);
        SizeChartUploadResult result = sizeChartService.uploadFromFile(adminStoreId, templateProduct.getId(), parseResult, source);

        log.info("Admin action: upload-global-brand-size-chart adminStoreId={} brandId={} version={}",
                adminStoreId, brandId, result.getVersion());

        return result;
    }

    public List<GlobalBrandSizeChartVersionResponse> listGlobalBrandSizeCharts(UUID brandId, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        Product templateProduct = findTemplateProductForGlobalBrand(brand, adminStoreId).orElse(null);
        if (templateProduct == null) {
            return List.of();
        }

        return sizeChartRepository.findAllByProductIdOrderByVersionDesc(templateProduct.getId()).stream()
                .map(sc -> new GlobalBrandSizeChartVersionResponse(
                        sc.getId(),
                        sc.getVersion(),
                        sc.isActive(),
                        sc.getSource(),
                        sc.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public void deactivateGlobalBrandActiveSizeChart(UUID brandId, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        Optional<Product> templateProduct = findTemplateProductForGlobalBrand(brand, adminStoreId);
        if (templateProduct.isPresent()) {
            sizeChartService.deactivateActiveSizeChart(adminStoreId, templateProduct.get().getId());
        }

        log.info("Admin action: deactivate-global-brand-size-chart adminStoreId={} brandId={}", adminStoreId, brandId);
    }

    public ScrapeJobResponse triggerBrandScrape(UUID brandId, UUID adminStoreId) {
        ScrapeJob pendingJob = scraperService.createPendingJobRecord(brandId);
        asyncScraperExecutor.execute(pendingJob.getId(), adminStoreId, scraperService);
        log.info("Admin action: trigger-brand-scrape adminStoreId={} brandId={} jobId={}",
                adminStoreId, brandId, pendingJob.getId());
        Brand brand = brandRepository.findGlobalById(brandId).orElse(null);
        String brandName = brand != null ? brand.getName() : null;
        return ScrapeJobResponse.from(pendingJob, brandName, brand != null ? brand.getLastScrapedAt() : null);
    }

    public List<ScrapeJobResponse> getBrandScrapeJobs(UUID brandId, UUID adminStoreId) {
        Brand brand = brandRepository.findGlobalById(brandId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Global brand not found"));

        List<ScrapeJobResponse> jobs = scraperService.listJobs(brandId).stream()
                .map(job -> ScrapeJobResponse.from(job, brand.getName(), brand.getLastScrapedAt()))
                .toList();

        log.info("Admin action: list-brand-scrape-jobs adminStoreId={} brandId={} count={}",
                adminStoreId, brandId, jobs.size());

        return jobs;
    }

    public Page<ScrapeJobResponse> getAllScrapeJobs(String status, Pageable pageable) {
        Page<ScrapeJob> page;
        if (status != null && !status.isBlank()) {
            try {
                ScrapeJobStatus parsedStatus = ScrapeJobStatus.valueOf(status.trim().toUpperCase());
                page = scraperService.listJobsByStatus(parsedStatus, pageable);
            } catch (IllegalArgumentException ex) {
                throw new FitVisionException(ErrorCode.VALIDATION_ERROR,
                        "Invalid status. Use PENDING, RUNNING, COMPLETED, or FAILED.");
            }
        } else {
            page = scraperService.listAllJobs(pageable);
        }
        return page.map(job -> ScrapeJobResponse.from(job, null, null));
    }

    public Page<AdminRecommendationView> getRecommendations(UUID tenantId,
                                                            UUID productId,
                                                            String quality,
                                                            Pageable pageable) {
        Page<RecommendationRequest> page = recommendationRequestRepository
                .findAdminRecommendations(tenantId, productId, normalizeQuality(quality), pageable);

        return page.map(this::toAdminRecommendationView);
    }

    @Transactional
    public void overridePlan(UUID storeId, String plan, UUID adminStoreId) {
        String normalized = plan == null ? "" : plan.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("FREE", "STARTER", "PRO", "TEAM").contains(normalized)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Invalid plan. Use FREE, STARTER, PRO, or TEAM.");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.STORE_NOT_FOUND, "Store not found"));
        store.setPlan(normalized);
        storeRepository.save(store);
        log.info("Admin action: override-plan adminStoreId={} targetStoreId={} plan={}", adminStoreId, storeId, normalized);
    }

    private StoreAdminView toStoreAdminView(Store store) {
        long totalProducts = productRepository.countByTenantIdAndDeletedAtIsNull(store.getId());
        long totalRecommendations = recommendationRequestRepository.countByTenantId(store.getId());
        LocalDateTime lastRecommendationAt = recommendationRequestRepository.findLastRecommendationAtByTenantId(store.getId());

        String maskedCustomerId = maskCustomerId(store.getStripeCustomerId());

        return new StoreAdminView(
                store.getId(),
                store.getName(),
                store.getEmail(),
                store.getPlan(),
                store.getRole(),
                store.getStatus(),
                store.getPlatform(),
                store.getCreatedAt(),
                totalProducts,
                totalRecommendations,
                lastRecommendationAt,
                store.getSubscriptionStatus(),
                maskedCustomerId,
                store.getSubscriptionCurrentPeriodEnd()
        );
    }

    private String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() < 8) return customerId;
        return customerId.substring(0, 4) + "****" + customerId.substring(customerId.length() - 4);
    }

    private BrandRecommendationStat toBrandStat(Object[] row) {
        UUID brandId = (UUID) row[0];
        String brandName = (String) row[1];
        long recommendationCount = ((Number) row[2]).longValue();
        double averageConfidence = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;

        return new BrandRecommendationStat(brandId, brandName, recommendationCount, averageConfidence);
    }

    private AdminRecommendationView toAdminRecommendationView(RecommendationRequest request) {
        Store store = storeRepository.findById(request.getTenantId()).orElse(null);
        Product product = productRepository.findById(request.getProductId()).orElse(null);

        return new AdminRecommendationView(
                request.getId(),
                request.getTenantId(),
                store != null ? store.getName() : "Unknown Store",
                request.getProductId(),
                product != null ? product.getName() : "Unknown Product",
                request.getRecommendedSize(),
                request.getConfidenceScore() != null ? request.getConfidenceScore().doubleValue() : 0.0,
                computeQuality(request),
                request.getCreatedAt()
        );
    }

    private String computeQuality(RecommendationRequest request) {
        if ("NO_MATCH".equals(request.getRecommendedSize()) || request.getConfidenceScore().doubleValue() == 0.0d) {
            return "NO_MATCH";
        }
        double score = request.getConfidenceScore().doubleValue();
        if (score >= 1.0d) {
            return "EXACT";
        }
        if (score >= 0.5d) {
            return "PARTIAL";
        }
        return "CLOSEST";
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_INACTIVE.equals(normalized) && !STATUS_ALL.equals(normalized)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Invalid status filter. Use ACTIVE, INACTIVE, or ALL.");
        }
        return normalized;
    }

    private String normalizeQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            return null;
        }
        String normalized = quality.trim().toUpperCase(Locale.ROOT);
        if (!QUALITY_KEYS.contains(normalized)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Invalid quality filter. Use EXACT, PARTIAL, CLOSEST, or NO_MATCH.");
        }
        return normalized;
    }

    private String slugify(String input) {
        String cleaned = input.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[^a-z0-9\\s-]", "");
        cleaned = cleaned.replaceAll("\\s+", "-");
        cleaned = cleaned.replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^-", "");
        return cleaned.replaceAll("-$", "");
    }

    private Product findOrCreateTemplateProductForGlobalBrand(Brand brand, UUID adminStoreId) {
        return findTemplateProductForGlobalBrand(brand, adminStoreId)
                .orElseGet(() -> {
                    Product p = new Product();
                    p.setId(UUID.randomUUID());
                    p.setTenantId(adminStoreId);
                    p.setBrandId(brand.getId());
                    p.setExternalProductId(templateExternalProductId(brand.getId()));
                    p.setName("Global Brand Template - " + brand.getName());
                    p.setCategory("GLOBAL");
                    p.setGenderTarget("UNISEX");
                    p.setCreatedAt(LocalDateTime.now());
                    p.setUpdatedAt(LocalDateTime.now());
                    return productRepository.save(p);
                });
    }

    private Optional<Product> findTemplateProductForGlobalBrand(Brand brand, UUID adminStoreId) {
        return productRepository.findAnyByExternalProductIdAndTenantId(templateExternalProductId(brand.getId()), adminStoreId)
                .map(p -> {
                    boolean changed = false;
                    if (p.getDeletedAt() != null) {
                        p.setDeletedAt(null);
                        changed = true;
                    }
                    if (!brand.getId().equals(p.getBrandId())) {
                        p.setBrandId(brand.getId());
                        changed = true;
                    }
                    if (changed) {
                        p.setUpdatedAt(LocalDateTime.now());
                        return productRepository.save(p);
                    }
                    return p;
                });
    }

    private String templateExternalProductId(UUID brandId) {
        return "__global_brand__" + brandId;
    }
}
