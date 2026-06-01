package com.fitvision.engine.recommendation;

import com.fitvision.domain.billing.PlanLimitsService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationEngine.
 * No Spring context — plain JUnit 5 + Mockito.
 * Repositories are mocked; BodyProfileCalculator and SizeChartMatcher are real.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationEngineTest {

    // --- Mocked dependencies ---
    @Mock private ProductRepository productRepository;
    @Mock private SizeChartRepository sizeChartRepository;
    @Mock private SizeEntryRepository sizeEntryRepository;
    @Mock private RecommendationRequestRepository recommendationRequestRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private PlanLimitsService planLimitsService;

    // --- Real stateless services ---
    private BodyProfileCalculator bodyProfileCalculator;
    private SizeChartMatcher sizeChartMatcher;

    private RecommendationEngine engine;

    // --- Shared fixture UUIDs ---
    private UUID tenantId;
    private UUID productId;
    private UUID brandId;
    private UUID sizeChartId;

    // --- Shared fixture objects ---
    private Product product;
    private SizeChart sizeChart;
    private Brand brand;

    @BeforeEach
    void setUp() {
        bodyProfileCalculator = new BodyProfileCalculator();
        sizeChartMatcher = new SizeChartMatcher();
        engine = new RecommendationEngine(
                bodyProfileCalculator, sizeChartMatcher,
                productRepository, sizeChartRepository,
                sizeEntryRepository, recommendationRequestRepository,
                brandRepository, planLimitsService);

        tenantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        brandId = UUID.randomUUID();
        sizeChartId = UUID.randomUUID();

        product = new Product();
        product.setId(productId);
        product.setName("Test Shirt");
        product.setBrandId(brandId);
        product.setTenantId(tenantId);
        product.setExternalProductId("EXT-001");
        product.setCategory("tops");
        product.setGenderTarget("unisex");

        sizeChart = new SizeChart();
        sizeChart.setId(sizeChartId);
        sizeChart.setProductId(productId);
        sizeChart.setActive(true);
        sizeChart.setVersion(1);
        sizeChart.setSource("uploaded");

        brand = Brand.builder()
                .id(brandId)
                .name("TestBrand")
                .slug("testbrand")
                .source("store_uploaded")
                .build();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void given_productAndSizeChartAndEntries_when_recommend_then_returnsRecommendationWithSize() {
        // Arrange: input for a 175cm/75kg male
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(false)
                .build();

        // A size entry that fully covers this profile's estimated measurements
        // male 175/75: chest≈121, waist≈59, hip≈127
        SizeEntry entryM = sizeEntry("M", 115, 130, 54, 65, 120, 135);

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(sizeChart));
        when(sizeEntryRepository.findAllBySizeChartId(sizeChartId))
                .thenReturn(List.of(entryM));
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        // Act
        RecommendationOutput output = engine.recommend(input);

        // Assert
        assertNotNull(output.getRecommendedSize());
        assertTrue(output.getConfidenceScore() > 0.0);
        assertNotEquals(MatchResult.MatchQuality.NO_MATCH, output.getQuality());
        assertTrue(output.isHasSizeChart());
        assertEquals("Test Shirt", output.getProductName());
        assertEquals("TestBrand", output.getBrandName());

        verify(recommendationRequestRepository).save(any(RecommendationRequest.class));
    }

    // -------------------------------------------------------------------------
    // Product not found
    // -------------------------------------------------------------------------

    @Test
    void given_productNotFound_when_recommend_then_throwsProductNotFoundException() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(false)
                .build();

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> engine.recommend(input));
        verifyNoInteractions(recommendationRequestRepository);
    }

    // -------------------------------------------------------------------------
    // No active size chart → graceful fallback
    // -------------------------------------------------------------------------

    @Test
    void given_noActiveSizeChart_when_recommend_then_returnsFallbackOutputWithoutPersisting() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(false)
                .build();

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.empty());
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        RecommendationOutput output = engine.recommend(input);

        assertFalse(output.isHasSizeChart());
        assertNull(output.getRecommendedSize());
        assertEquals(0.0, output.getConfidenceScore(), 0.001);
        assertEquals(MatchResult.MatchQuality.NO_MATCH, output.getQuality());
        assertNull(output.getFallbackUrl());

        // No analytics record persisted for fallback (no size chart to match against)
        verifyNoInteractions(recommendationRequestRepository);
    }

    // -------------------------------------------------------------------------
    // Empty size chart (size chart exists but has no entries)
    // -------------------------------------------------------------------------

    @Test
    void given_emptySizeChart_when_recommend_then_returnsNoMatchAndPersistsRecord() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(false)
                .build();

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(sizeChart));
        when(sizeEntryRepository.findAllBySizeChartId(sizeChartId))
                .thenReturn(List.of()); // no entries
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        RecommendationOutput output = engine.recommend(input);

        assertTrue(output.isHasSizeChart()); // chart exists
        assertEquals(MatchResult.MatchQuality.NO_MATCH, output.getQuality());
        assertEquals(0.0, output.getConfidenceScore(), 0.001);

        // Analytics record must still be persisted
        verify(recommendationRequestRepository).save(any(RecommendationRequest.class));
    }

    // -------------------------------------------------------------------------
    // GDPR: storeBodyData=false → measurements zeroed out
    // -------------------------------------------------------------------------

    @Test
    void given_storeBodyDataFalse_when_recommend_then_persistedRecordHasMeasurementsZeroed() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(false) // no consent
                .build();

        SizeEntry entryM = sizeEntry("M", 115, 130, 54, 65, 120, 135);

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(sizeChart));
        when(sizeEntryRepository.findAllBySizeChartId(sizeChartId))
                .thenReturn(List.of(entryM));
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        engine.recommend(input);

        ArgumentCaptor<RecommendationRequest> captor =
                ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(recommendationRequestRepository).save(captor.capture());
        RecommendationRequest saved = captor.getValue();

        assertFalse(saved.isBodyMeasurementsStored());
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getHeightCm()),
                "Height must be zeroed when storeBodyData=false");
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getWeightKg()),
                "Weight must be zeroed when storeBodyData=false");
    }

    // -------------------------------------------------------------------------
    // GDPR: storeBodyData=true → measurements stored as supplied
    // -------------------------------------------------------------------------

    @Test
    void given_storeBodyDataTrue_when_recommend_then_persistedRecordHasActualMeasurements() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(Gender.MALE)
                .age(30)
                .storeBodyData(true) // explicit consent
                .build();

        SizeEntry entryM = sizeEntry("M", 115, 130, 54, 65, 120, 135);

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(sizeChart));
        when(sizeEntryRepository.findAllBySizeChartId(sizeChartId))
                .thenReturn(List.of(entryM));
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        engine.recommend(input);

        ArgumentCaptor<RecommendationRequest> captor =
                ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(recommendationRequestRepository).save(captor.capture());
        RecommendationRequest saved = captor.getValue();

        assertTrue(saved.isBodyMeasurementsStored());
        assertEquals(0, BigDecimal.valueOf(175).compareTo(saved.getHeightCm()),
                "Height must be stored when storeBodyData=true");
        assertEquals(0, BigDecimal.valueOf(75).compareTo(saved.getWeightKg()),
                "Weight must be stored when storeBodyData=true");
    }

    // -------------------------------------------------------------------------
    // Gender defaults to UNISEX when null
    // -------------------------------------------------------------------------

    @Test
    void given_nullGender_when_recommend_then_usesUnisexGenderAndDoesNotThrow() {
        RecommendationInput input = RecommendationInput.builder()
                .tenantId(tenantId)
                .productId(productId)
                .heightCm(175)
                .weightKg(75)
                .gender(null) // should default to UNISEX
                .age(30)
                .storeBodyData(false)
                .build();

        SizeEntry entryM = sizeEntry("M", 115, 130, 54, 65, 120, 135);

        when(productRepository.findByIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(product));
        when(sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId))
                .thenReturn(Optional.of(sizeChart));
        when(sizeEntryRepository.findAllBySizeChartId(sizeChartId))
                .thenReturn(List.of(entryM));
        when(brandRepository.findById(brandId))
                .thenReturn(Optional.of(brand));

        RecommendationOutput output = assertDoesNotThrow(() -> engine.recommend(input));
        assertNotNull(output);

        // Verify that the saved record uses "UNISEX" as the gender string
        ArgumentCaptor<RecommendationRequest> captor =
                ArgumentCaptor.forClass(RecommendationRequest.class);
        verify(recommendationRequestRepository).save(captor.capture());
        assertEquals("UNISEX", captor.getValue().getGender());
    }

    // -------------------------------------------------------------------------
    // Helper factory
    // -------------------------------------------------------------------------

    /** Creates a SizeEntry with chest, waist, and hip bounds defined. */
    private SizeEntry sizeEntry(String label,
                                double chestMin, double chestMax,
                                double waistMin, double waistMax,
                                double hipMin, double hipMax) {
        SizeEntry e = new SizeEntry();
        e.setId(UUID.randomUUID());
        e.setSizeLabel(label);
        e.setChestMin(BigDecimal.valueOf(chestMin));
        e.setChestMax(BigDecimal.valueOf(chestMax));
        e.setWaistMin(BigDecimal.valueOf(waistMin));
        e.setWaistMax(BigDecimal.valueOf(waistMax));
        e.setHipMin(BigDecimal.valueOf(hipMin));
        e.setHipMax(BigDecimal.valueOf(hipMax));
        return e;
    }
}
