package com.fitvision.api.dashboard.recommendation;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.engine.recommendation.RecommendationEngine;
import com.fitvision.engine.recommendation.RecommendationInput;
import com.fitvision.engine.recommendation.SimulationResult;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Store-owner recommendation simulator. Runs the real engine as a dry run — no analytics
 * row, no plan-limit consumption — so the merchant can see how a size would be recommended
 * and why.
 */
@RestController
@RequestMapping("/api/dashboard/v1/recommendations")
@Tag(name = "Dashboard")
public class SimulatorController {

    private final RecommendationEngine recommendationEngine;

    public SimulatorController(RecommendationEngine recommendationEngine) {
        this.recommendationEngine = recommendationEngine;
    }

    @PostMapping("/simulate")
    public ResponseEntity<ApiResponse<SimulateResponse>> simulate(@Valid @RequestBody SimulateRequest request) {
        UUID tenantId = TenantContext.get();

        RecommendationInput.Builder input = RecommendationInput.builder()
                .tenantId(tenantId)
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .gender(parseGender(request.getGender()))
                .age(request.getAge())
                .storeBodyData(false);

        UUID productUuid = tryUuid(request.getProductId());
        if (productUuid != null) {
            input.productId(productUuid);
        } else {
            input.externalProductId(request.productRef());
        }

        SimulationResult result = recommendationEngine.simulate(input.build());
        return ResponseEntity.ok(ApiResponse.ok(SimulateResponse.from(result)));
    }

    private static Gender parseGender(String raw) {
        if (raw == null) return Gender.UNISEX;
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Gender.UNISEX;
        }
    }

    private static UUID tryUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
