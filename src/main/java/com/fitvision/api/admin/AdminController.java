package com.fitvision.api.admin;

import com.fitvision.api.dashboard.brand.BrandResponse;
import com.fitvision.domain.admin.AdminHealthService;
import com.fitvision.domain.admin.AdminService;
import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeChartFileParser;
import com.fitvision.domain.sizechart.SizeChartUploadResult;
import com.fitvision.infrastructure.parsing.SizeChartParserFactory;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1")
@Validated
@Tag(name = "Admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final AdminHealthService adminHealthService;
    private final SizeChartParserFactory parserFactory;

    public AdminController(AdminService adminService,
                           AdminHealthService adminHealthService,
                           SizeChartParserFactory parserFactory) {
        this.adminService = adminService;
        this.adminHealthService = adminHealthService;
        this.parserFactory = parserFactory;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<AdminHealthResponse>> getHealth() {
        return ResponseEntity.ok(ApiResponse.ok(adminHealthService.getHealth()));
    }

    @GetMapping("/recommendations/stats")
    public ResponseEntity<ApiResponse<RecommendationStatsResponse>> getRecommendationStats() {
        return ResponseEntity.ok(ApiResponse.ok(
                adminHealthService.getRecommendationStats(java.time.Duration.ofHours(24))));
    }

    @PostMapping("/scrape-jobs/trigger-all")
    public ResponseEntity<ApiResponse<ScrapeTriggerAllResponse>> triggerAllScrapes() {
        UUID adminStoreId = requireAdminStoreId();
        return ResponseEntity.ok(ApiResponse.ok(adminHealthService.triggerAllScrapes(adminStoreId)));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<AdminMetricsResponse>> getMetrics() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getMetrics()));
    }

    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<Page<StoreAdminView>>> getStores(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "") String search) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<StoreAdminView> stores = adminService.getStores(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(stores));
    }

    @GetMapping("/stores/{storeId}")
    public ResponseEntity<ApiResponse<StoreAdminView>> getStore(@PathVariable UUID storeId) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getStore(storeId)));
    }

    @PatchMapping("/stores/{storeId}/status")
    public ResponseEntity<ApiResponse<StoreAdminView>> updateStoreStatus(@PathVariable UUID storeId,
                                                                          @Valid @RequestBody UpdateStoreStatusRequest request) {
        UUID adminStoreId = requireAdminStoreId();
        StoreAdminView updated = adminService.updateStoreStatus(storeId, request.status(), adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(updated));
    }

    @PatchMapping("/stores/{storeId}/plan")
    public ResponseEntity<ApiResponse<Void>> overrideStorePlan(@PathVariable UUID storeId,
                                                               @RequestBody java.util.Map<String, String> body) {
        UUID adminStoreId = requireAdminStoreId();
        String plan = body.getOrDefault("plan", "");
        adminService.overridePlan(storeId, plan, adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getBrands() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.getBrands()));
    }

    @PostMapping("/brands")
    public ResponseEntity<ApiResponse<BrandResponse>> createGlobalBrand(@Valid @RequestBody GlobalBrandRequest request) {
        UUID adminStoreId = requireAdminStoreId();
        BrandResponse response = adminService.createGlobalBrand(request.name(), adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/brands/{id}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateGlobalBrand(@PathVariable UUID id,
                                                                         @Valid @RequestBody GlobalBrandRequest request) {
        UUID adminStoreId = requireAdminStoreId();
        BrandResponse response = adminService.updateGlobalBrand(id, request.name(), adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/brands/{id}")
    public ResponseEntity<Void> deleteGlobalBrand(@PathVariable UUID id) {
        UUID adminStoreId = requireAdminStoreId();
        adminService.deleteGlobalBrand(id, adminStoreId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/brands/{brandId}/size-charts/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SizeChartUploadResult>> uploadGlobalBrandSizeChart(
            @PathVariable UUID brandId,
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID adminStoreId = requireAdminStoreId();

        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        SizeChartFileParser parser = parserFactory.getParser(contentType, filename);
        ParseResult parseResult;
        try (var inputStream = file.getInputStream()) {
            parseResult = parser.parse(inputStream);
        }

        String source = filename.toLowerCase().endsWith(".csv") ? "admin_global_csv" : "admin_global_xlsx";
        SizeChartUploadResult response = adminService.uploadGlobalBrandSizeChart(brandId, parseResult, source, adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/brands/{brandId}/size-charts")
    public ResponseEntity<ApiResponse<List<GlobalBrandSizeChartVersionResponse>>> getGlobalBrandSizeCharts(@PathVariable UUID brandId) {
        UUID adminStoreId = requireAdminStoreId();
        List<GlobalBrandSizeChartVersionResponse> charts = adminService.listGlobalBrandSizeCharts(brandId, adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(charts));
    }

    @DeleteMapping("/brands/{brandId}/size-charts/active")
    public ResponseEntity<Void> deactivateGlobalBrandActiveSizeChart(@PathVariable UUID brandId) {
        UUID adminStoreId = requireAdminStoreId();
        adminService.deactivateGlobalBrandActiveSizeChart(brandId, adminStoreId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/brands/{brandId}/scrape")
    public ResponseEntity<ApiResponse<ScrapeJobResponse>> triggerBrandScrape(@PathVariable UUID brandId) {
        UUID adminStoreId = requireAdminStoreId();
        ScrapeJobResponse response = adminService.triggerBrandScrape(brandId, adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/brands/{brandId}/scrape-jobs")
    public ResponseEntity<ApiResponse<List<ScrapeJobResponse>>> getBrandScrapeJobs(@PathVariable UUID brandId) {
        UUID adminStoreId = requireAdminStoreId();
        List<ScrapeJobResponse> jobs = adminService.getBrandScrapeJobs(brandId, adminStoreId);
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    @GetMapping("/scrape-jobs")
    public ResponseEntity<ApiResponse<Page<ScrapeJobResponse>>> getAllScrapeJobs(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ScrapeJobResponse> jobs = adminService.getAllScrapeJobs(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<Page<AdminRecommendationView>>> getRecommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String quality) {

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<AdminRecommendationView> data = adminService.getRecommendations(tenantId, productId, quality, pageable);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private UUID requireAdminStoreId() {
        UUID adminStoreId = TenantContext.get();
        if (adminStoreId == null) {
            log.warn("Admin action attempted without TenantContext adminStoreId");
            throw new FitVisionException(ErrorCode.UNAUTHORIZED, "Unauthorized");
        }
        return adminStoreId;
    }
}
