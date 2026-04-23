package com.fitvision.engine.recommendation;

import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.recommendation.Gender;
import com.fitvision.domain.recommendation.RecommendationRequest;
import com.fitvision.domain.sizechart.SizeChart;
import com.fitvision.domain.sizechart.SizeEntry;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.RecommendationRequestRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.SizeEntryRepository;
import com.fitvision.shared.exception.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the full size-recommendation flow.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Validate &amp; compute {@link BodyProfile} (delegates to {@link BodyProfileCalculator})</li>
 *   <li>Resolve the {@link Product}</li>
 *   <li>Find the active {@link SizeChart} — return graceful fallback if absent</li>
 *   <li>Load {@link SizeEntry} rows</li>
 *   <li>Match via {@link SizeChartMatcher}</li>
 *   <li>Persist analytics record</li>
 *   <li>Return {@link RecommendationOutput}</li>
 * </ol>
 */
@Service
public class RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngine.class);

    /** Stored in recommendation_requests when the engine produces no size match. */
    private static final String NO_MATCH_SIZE_LABEL = "NO_MATCH";

    private final BodyProfileCalculator bodyProfileCalculator;
    private final SizeChartMatcher sizeChartMatcher;
    private final ProductRepository productRepository;
    private final SizeChartRepository sizeChartRepository;
    private final SizeEntryRepository sizeEntryRepository;
    private final RecommendationRequestRepository recommendationRequestRepository;
    private final BrandRepository brandRepository;

    public RecommendationEngine(BodyProfileCalculator bodyProfileCalculator,
                                SizeChartMatcher sizeChartMatcher,
                                ProductRepository productRepository,
                                SizeChartRepository sizeChartRepository,
                                SizeEntryRepository sizeEntryRepository,
                                RecommendationRequestRepository recommendationRequestRepository,
                                BrandRepository brandRepository) {
        this.bodyProfileCalculator = bodyProfileCalculator;
        this.sizeChartMatcher = sizeChartMatcher;
        this.productRepository = productRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.sizeEntryRepository = sizeEntryRepository;
        this.recommendationRequestRepository = recommendationRequestRepository;
        this.brandRepository = brandRepository;
    }

    /**
     * Produces a size recommendation for the given input.
     *
     * <p>The persist step is transactional. If an exception is thrown after persistence begins,
     * the analytics record is rolled back alongside the recommendation.
     *
     * @param input recommendation input with buyer measurements and tenant context
     * @return recommendation output; never null
     * @throws com.fitvision.shared.exception.InvalidBodyMeasurementException if height/weight are out of range
     * @throws ProductNotFoundException if the product does not exist for the given tenant
     */
    @Transactional
    public RecommendationOutput recommend(RecommendationInput input) {
        Gender gender = input.getGender() != null ? input.getGender() : Gender.UNISEX;

        // Step 1: Validate inputs and compute BodyProfile.
        // BodyProfileCalculator throws InvalidBodyMeasurementException on out-of-range values.
        BodyProfile profile = bodyProfileCalculator.calculate(
                input.getHeightCm(), input.getWeightKg(), gender, input.getAge());
        log.debug("BodyProfile computed: {}", profile);

        // Step 2: Resolve product — tenant-scoped.
        // Support lookup by UUID (dashboard) or by externalProductId (widget).
        Product product;
        if (input.getProductId() != null) {
            product = productRepository.findByIdAndTenantId(input.getProductId(), input.getTenantId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found: productId=" + input.getProductId()
                                    + ", tenantId=" + input.getTenantId()));
        } else {
            product = productRepository
                    .findByExternalProductIdAndTenantId(input.getExternalProductId(), input.getTenantId())
                    .orElseThrow(() -> new ProductNotFoundException(
                            "Product not found: externalProductId=" + input.getExternalProductId()
                                    + ", tenantId=" + input.getTenantId()));
        }

        String productName = product.getName();
        String brandName = brandRepository.findById(product.getBrandId())
                .map(Brand::getName)
                .orElse(null);

        // Step 3: Find active size chart — graceful fallback if absent (no exception).
        Optional<SizeChart> sizeChartOpt = sizeChartRepository
                .findActiveByProductIdAndTenantId(product.getId(), input.getTenantId());

        if (sizeChartOpt.isEmpty()) {
            log.info("Recommendation fallback: tenantId={}, productId={} — no active size chart.",
                    input.getTenantId(), product.getId());
            return RecommendationOutput.builder()
                    .recommendedSize(null)
                    .confidenceScore(0.0)
                    .quality(MatchResult.MatchQuality.NO_MATCH)
                    .productName(productName)
                    .brandName(brandName)
                    .hasSizeChart(false)
                    .fallbackUrl(null)
                    .build();
        }

        // Step 4: Load size entries.
        List<SizeEntry> entries = sizeEntryRepository.findAllBySizeChartId(sizeChartOpt.get().getId());

        // Step 5: Match BodyProfile against size entries.
        MatchResult matchResult = sizeChartMatcher.match(profile, entries);

        // Step 6: Persist analytics record (GDPR-aware).
        persistAnalytics(input, product, gender, matchResult);

        log.info("Recommendation: tenantId={}, productId={}, recommendedSize={}, confidenceScore={}, quality={}",
                input.getTenantId(), product.getId(),
                matchResult.getRecommendedSize(), matchResult.getConfidenceScore(), matchResult.getQuality());

        // Step 7: Return output.
        return RecommendationOutput.builder()
                .recommendedSize(matchResult.getRecommendedSize())
                .confidenceScore(matchResult.getConfidenceScore())
                .quality(matchResult.getQuality())
                .productName(productName)
                .brandName(brandName)
                .hasSizeChart(true)
                .fallbackUrl(null)
                .build();
    }

    /**
     * Persists a {@link RecommendationRequest} analytics record.
     *
     * <p>GDPR: when {@code storeBodyData} is false, height and weight are zeroed out
     * rather than stored, but the record itself is always saved for analytics.
     */
    private void persistAnalytics(RecommendationInput input,
                                   Product product,
                                   Gender gender,
                                   MatchResult matchResult) {
        boolean store = input.isStoreBodyData();
        BigDecimal heightCm = store
                ? BigDecimal.valueOf(input.getHeightCm())
                : BigDecimal.ZERO;
        BigDecimal weightKg = store
                ? BigDecimal.valueOf(input.getWeightKg())
                : BigDecimal.ZERO;

        // recommended_size is NOT NULL in the schema — use a sentinel when no match was found.
        String recommendedSize = matchResult.getRecommendedSize() != null
                ? matchResult.getRecommendedSize()
                : NO_MATCH_SIZE_LABEL;

        RecommendationRequest entity = RecommendationRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(input.getTenantId())
                .productId(product.getId())
                .heightCm(heightCm)
                .weightKg(weightKg)
                .gender(gender.name())
                .age(input.getAge())
                .recommendedSize(recommendedSize)
                .confidenceScore(BigDecimal.valueOf(matchResult.getConfidenceScore()))
                .bodyMeasurementsStored(store)
                .createdAt(LocalDateTime.now())
                .build();

        recommendationRequestRepository.save(entity);
    }
}
