package com.fitvision.api.dashboard.analytics;

import com.fitvision.domain.recommendation.AnalyticsService;
import com.fitvision.domain.recommendation.RecommendationRequest;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/analytics")
@Tag(name = "Dashboard")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AnalyticsResponse>> getSummary() {
        UUID tenantId = TenantContext.get();
        AnalyticsResponse summary = analyticsService.getSummary(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<Page<RecommendationRequest>>> getRecommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = TenantContext.get();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<RecommendationRequest> recommendations = analyticsService.getRecommendations(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(recommendations));
    }
}
