package com.fitvision.domain.recommendation;

import com.fitvision.api.dashboard.analytics.AnalyticsResponse;
import com.fitvision.api.dashboard.analytics.ProductHealthRow;
import com.fitvision.api.dashboard.analytics.ProductRecommendationStat;
import com.fitvision.domain.product.Product;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.RecommendationRequestRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AnalyticsService {

    private static final List<String> QUALITY_KEYS = List.of("EXACT", "PARTIAL", "CLOSEST", "NO_MATCH");

    private final RecommendationRequestRepository recommendationRequestRepository;
    private final ProductRepository productRepository;
    private final SizeChartRepository sizeChartRepository;

    public AnalyticsService(RecommendationRequestRepository recommendationRequestRepository,
                            ProductRepository productRepository,
                            SizeChartRepository sizeChartRepository) {
        this.recommendationRequestRepository = recommendationRequestRepository;
        this.productRepository = productRepository;
        this.sizeChartRepository = sizeChartRepository;
    }

    /**
     * Per-product "needs attention" signals, most-urgent first: no size chart, a high
     * inconclusive (NO_MATCH) rate, or low average confidence — weighted by how much the
     * product is actually used. These are chart/engine-quality proxies, not measured
     * return outcomes.
     */
    public List<ProductHealthRow> getProductHealth(UUID tenantId) {
        List<Product> products = productRepository.findAllByTenantId(tenantId);
        if (products.isEmpty()) {
            return List.of();
        }

        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Set<UUID> withChart = sizeChartRepository.findActiveProductIdsByProductIds(productIds);

        Map<UUID, long[]> stats = new HashMap<>(); // productId -> [total, noMatch]
        Map<UUID, Double> avgConfidence = new HashMap<>();
        for (Object[] row : recommendationRequestRepository.findProductHealthByTenantId(tenantId)) {
            UUID pid = (UUID) row[0];
            long total = ((Number) row[1]).longValue();
            long noMatch = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            double avg = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            stats.put(pid, new long[] {total, noMatch});
            avgConfidence.put(pid, avg);
        }

        long maxVolume = stats.values().stream().mapToLong(s -> s[0]).max().orElse(1L);

        List<ProductHealthRow> rows = new ArrayList<>();
        for (Product product : products) {
            long[] s = stats.getOrDefault(product.getId(), new long[] {0L, 0L});
            long total = s[0];
            long noMatch = s[1];
            boolean hasChart = withChart.contains(product.getId());
            double noMatchRate = total > 0 ? (double) noMatch / total : 0.0;
            double avgConf = avgConfidence.getOrDefault(product.getId(), 0.0);

            List<String> reasons = new ArrayList<>();
            double score = 0.0;

            if (!hasChart) {
                reasons.add("NO_SIZE_CHART");
                score += 60;
            }
            if (total > 0 && noMatchRate >= 0.15) {
                reasons.add("HIGH_NO_MATCH");
                score += Math.min(30, noMatchRate * 100);
            }
            if (hasChart && total >= 5 && avgConf < 0.5) {
                reasons.add("LOW_CONFIDENCE");
                score += (0.5 - avgConf) * 60;
            }

            // Weight by usage: a heavily-used product with issues matters more.
            double volumeWeight = 0.7 + 0.3 * Math.min(1.0, (double) total / maxVolume);
            int attentionScore = (int) Math.round(Math.min(100.0, score * volumeWeight));

            if (attentionScore <= 0) {
                continue;
            }

            rows.add(new ProductHealthRow(
                    product.getId().toString(),
                    product.getName(),
                    hasChart,
                    total,
                    noMatch,
                    round2(noMatchRate),
                    round2(avgConf),
                    attentionScore,
                    reasons
            ));
        }

        rows.sort(Comparator.comparingInt(ProductHealthRow::attentionScore).reversed()
                .thenComparing(Comparator.comparingLong(ProductHealthRow::totalRecommendations).reversed()));
        return rows;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
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
