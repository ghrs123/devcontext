package com.fitvision.domain.recommendation;

import com.fitvision.api.dashboard.analytics.AnalyticsResponse;
import com.fitvision.api.dashboard.analytics.ProductRecommendationStat;
import com.fitvision.infrastructure.persistence.RecommendationRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final List<String> QUALITY_KEYS = List.of("EXACT", "PARTIAL", "CLOSEST", "NO_MATCH");

    private final RecommendationRequestRepository recommendationRequestRepository;

    public AnalyticsService(RecommendationRequestRepository recommendationRequestRepository) {
        this.recommendationRequestRepository = recommendationRequestRepository;
    }

    public AnalyticsResponse getSummary(UUID tenantId) {
        long totalRecommendations = recommendationRequestRepository.countByTenantId(tenantId);
        long recommendationsLast30Days = recommendationRequestRepository
                .countByTenantIdAndCreatedAtAfter(tenantId, LocalDateTime.now().minusDays(30));

        Double averageConfidence = recommendationRequestRepository.findAverageConfidenceByTenantId(tenantId);
        double averageConfidenceScore = averageConfidence != null ? averageConfidence : 0.0;

        Map<String, Long> qualityDistribution = new LinkedHashMap<>();
        for (String quality : QUALITY_KEYS) {
            qualityDistribution.put(quality,
                    recommendationRequestRepository.countByTenantIdAndQuality(tenantId, quality));
        }

        List<ProductRecommendationStat> topProducts = recommendationRequestRepository
                .findTopProductsByTenantId(tenantId, Pageable.ofSize(5))
                .stream()
                .map(this::toProductRecommendationStat)
                .toList();

        return new AnalyticsResponse(
                totalRecommendations,
                recommendationsLast30Days,
                averageConfidenceScore,
                qualityDistribution,
                topProducts
        );
    }

    public Page<RecommendationRequest> getRecommendations(UUID tenantId, Pageable pageable) {
        return recommendationRequestRepository.findAllByTenantId(tenantId, pageable);
    }

    private ProductRecommendationStat toProductRecommendationStat(Object[] row) {
        UUID productId = (UUID) row[0];
        String productName = (String) row[1];
        long recommendationCount = ((Number) row[2]).longValue();
        double averageConfidence = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
        return new ProductRecommendationStat(productId, productName, recommendationCount, averageConfidence);
    }
}
