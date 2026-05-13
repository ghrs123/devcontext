package com.fitvision.api.widget;

import com.fitvision.domain.recommendation.Gender;
import com.fitvision.engine.recommendation.RecommendationEngine;
import com.fitvision.engine.recommendation.RecommendationInput;
import com.fitvision.engine.recommendation.RecommendationOutput;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Widget API endpoint for size recommendations.
 *
 * <p>Authenticated via {@code X-FitVision-Key}. The resolved tenant UUID is read from
 * {@link TenantContext} (set by {@code ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/widget/v1")
@Tag(name = "Widget")
public class WidgetRecommendationController {

    private static final Logger log = LoggerFactory.getLogger(WidgetRecommendationController.class);

    private final RecommendationEngine recommendationEngine;

    public WidgetRecommendationController(RecommendationEngine recommendationEngine) {
        this.recommendationEngine = recommendationEngine;
    }

    /**
     * Returns a size recommendation for the given product and buyer measurements.
     *
     * <p>Always returns HTTP 200. Business outcomes (no size chart, no match) are encoded
     * in the response body via {@code quality} and {@code hasSizeChart} fields.
     *
     * @param request validated request body containing buyer measurements and product reference
     * @return wrapped {@link SizeRecommendationResponse}
     */
    @PostMapping("/size-recommendation")
    public ResponseEntity<ApiResponse<SizeRecommendationResponse>> recommend(
            @Valid @RequestBody SizeRecommendationRequest request) {

        UUID tenantId = TenantContext.get();

        Gender gender = parseGender(request.getGender());

        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .externalProductId(request.getExternalProductId())
                .heightCm(request.getHeightCm())
                .weightKg(request.getWeightKg())
                .gender(gender)
                .age(request.getAge())
                .storeBodyData(request.isStoreBodyData())
                .build();

        RecommendationOutput output = recommendationEngine.recommend(input);

        SizeRecommendationResponse response = SizeRecommendationResponse.from(output);

        log.debug("Widget recommendation served: tenantId={}, externalProductId={}, quality={}",
                tenantId, request.getExternalProductId(), response.getQuality());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Safely parses a gender string. Returns {@code null} when input is null (the engine
     * will default to UNISEX). Returns UNISEX for any unrecognised value, never throws.
     */
    private Gender parseGender(String genderStr) {
        if (genderStr == null || genderStr.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(genderStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.debug("Unrecognised gender value '{}'; defaulting to UNISEX", genderStr);
            return Gender.UNISEX;
        }
    }
}
