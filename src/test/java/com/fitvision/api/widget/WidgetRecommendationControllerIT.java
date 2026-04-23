package com.fitvision.api.widget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.sizechart.SizeChart;
import com.fitvision.domain.sizechart.SizeEntry;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.infrastructure.persistence.SizeEntryRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for POST /api/widget/v1/size-recommendation.
 *
 * <p>Each test method is fully isolated: @BeforeEach inserts fresh test data with unique
 * IDs, and @AfterEach deletes all data that was created.
 *
 * <p>Measurements used in the happy-path tests: height=175cm, weight=75kg, gender=MALE.
 * Based on the BodyProfileCalculator formulas, the estimated measurements will be
 * approximately chest=121cm, waist=59cm, hip=127cm — which fall within the "M" SizeEntry
 * ranges inserted in @BeforeEach.
 */
@AutoConfigureMockMvc
class WidgetRecommendationControllerIT extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/widget/v1/size-recommendation";
    private static final String API_KEY_HEADER = "X-FitVision-Key";

    // Test data identifiers — set in @BeforeEach, used in @AfterEach
    private UUID storeId;
    private String testApiKey;
    private UUID brandId;
    private UUID productWithChartId;
    private UUID productNoChartId;
    private UUID sizeChartId;
    private UUID sizeEntryMId;
    private UUID sizeEntryLId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SizeChartRepository sizeChartRepository;

    @Autowired
    private SizeEntryRepository sizeEntryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Use unique keys/slugs per test to avoid constraint violations in case a
        // previous run left behind orphaned rows.
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        testApiKey = "it-key-" + uniqueSuffix;

        // --- Store ---
        storeId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("IT Test Store")
                .email("it-store-" + uniqueSuffix + "@test.com")
                .plan("starter")
                .status("ACTIVE")
                .apiKeyPublic(testApiKey)
                .apiKeySecret("secret-" + uniqueSuffix)
                .platform("shopify")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        storeRepository.save(store);

        // --- Brand ---
        brandId = UUID.randomUUID();
        Brand brand = Brand.builder()
                .id(brandId)
                .tenantId(storeId)
                .name("IT Test Brand")
                .slug("it-brand-" + uniqueSuffix)
                .source("store_uploaded")
                .createdAt(LocalDateTime.now())
                .build();
        brandRepository.save(brand);

        // --- Product with an active size chart (happy path) ---
        productWithChartId = UUID.randomUUID();
        Product productWithChart = new Product();
        productWithChart.setId(productWithChartId);
        productWithChart.setBrandId(brandId);
        productWithChart.setTenantId(storeId);
        productWithChart.setExternalProductId("shopify-001-" + uniqueSuffix);
        productWithChart.setName("IT Test Shirt");
        productWithChart.setCategory("tops");
        productWithChart.setGenderTarget("male");
        productWithChart.setCreatedAt(LocalDateTime.now());
        productWithChart.setUpdatedAt(LocalDateTime.now());
        productRepository.save(productWithChart);

        // --- Product with NO size chart ---
        productNoChartId = UUID.randomUUID();
        Product productNoChart = new Product();
        productNoChart.setId(productNoChartId);
        productNoChart.setBrandId(brandId);
        productNoChart.setTenantId(storeId);
        productNoChart.setExternalProductId("shopify-no-chart-" + uniqueSuffix);
        productNoChart.setName("IT Test Trousers (no chart)");
        productNoChart.setCategory("bottoms");
        productNoChart.setGenderTarget("male");
        productNoChart.setCreatedAt(LocalDateTime.now());
        productNoChart.setUpdatedAt(LocalDateTime.now());
        productRepository.save(productNoChart);

        // --- Active SizeChart for productWithChart ---
        sizeChartId = UUID.randomUUID();
        SizeChart sizeChart = new SizeChart();
        sizeChart.setId(sizeChartId);
        sizeChart.setProductId(productWithChartId);
        sizeChart.setVersion(1);
        sizeChart.setSource("manual");
        sizeChart.setActive(true);
        sizeChart.setCreatedAt(LocalDateTime.now());
        sizeChartRepository.save(sizeChart);

        // --- SizeEntry "M" — designed to match 175cm/75kg/MALE buyer ---
        // Calculated estimates: chest≈121cm, waist≈59cm, hip≈127cm
        sizeEntryMId = UUID.randomUUID();
        SizeEntry entryM = new SizeEntry();
        entryM.setId(sizeEntryMId);
        entryM.setSizeChartId(sizeChartId);
        entryM.setSizeLabel("M");
        entryM.setChestMin(new BigDecimal("115.0"));
        entryM.setChestMax(new BigDecimal("125.0"));
        entryM.setWaistMin(new BigDecimal("55.0"));
        entryM.setWaistMax(new BigDecimal("65.0"));
        entryM.setHipMin(new BigDecimal("122.0"));
        entryM.setHipMax(new BigDecimal("133.0"));
        sizeEntryRepository.save(entryM);

        // --- SizeEntry "L" — larger range ---
        sizeEntryLId = UUID.randomUUID();
        SizeEntry entryL = new SizeEntry();
        entryL.setId(sizeEntryLId);
        entryL.setSizeChartId(sizeChartId);
        entryL.setSizeLabel("L");
        entryL.setChestMin(new BigDecimal("126.0"));
        entryL.setChestMax(new BigDecimal("136.0"));
        entryL.setWaistMin(new BigDecimal("66.0"));
        entryL.setWaistMax(new BigDecimal("76.0"));
        entryL.setHipMin(new BigDecimal("133.0"));
        entryL.setHipMax(new BigDecimal("143.0"));
        sizeEntryRepository.save(entryL);
    }

    @AfterEach
    void tearDown() {
        // Delete in FK-safe order: analytics → entries → charts → products → brands → stores
        jdbcTemplate.update("DELETE FROM recommendation_requests WHERE tenant_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM size_entries WHERE id = ? OR id = ?",
                sizeEntryMId, sizeEntryLId);
        if (sizeChartId != null) {
            sizeChartRepository.deleteById(sizeChartId);
        }
        if (productWithChartId != null) {
            productRepository.deleteById(productWithChartId);
        }
        if (productNoChartId != null) {
            productRepository.deleteById(productNoChartId);
        }
        if (brandId != null) {
            brandRepository.deleteById(brandId);
        }
        if (storeId != null) {
            storeRepository.deleteById(storeId);
        }
    }

    // -----------------------------------------------------------------------
    // Scenario 1: Missing API key header → HTTP 401
    // -----------------------------------------------------------------------

    @Test
    void scenario1_missingApiKey_returns401WithInvalidApiKeyCode() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequestBody("shopify-001-ignored", 175.0, 75.0, "MALE", null, false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    // -----------------------------------------------------------------------
    // Scenario 2: Invalid API key → HTTP 401
    // -----------------------------------------------------------------------

    @Test
    void scenario2_invalidApiKey_returns401WithInvalidApiKeyCode() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(API_KEY_HEADER, "totally-invalid-api-key-99999")
                        .content(buildRequestBody("shopify-001-ignored", 175.0, 75.0, "MALE", null, false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    // -----------------------------------------------------------------------
    // Scenario 3: Valid API key + product + size chart → HTTP 200, match found
    // -----------------------------------------------------------------------

    @Test
    void scenario3_validKeyAndProductWithSizeChart_returns200WithRecommendedSize() throws Exception {
        String externalId = "shopify-001-" + getUniqueSuffixFromApiKey();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(API_KEY_HEADER, testApiKey)
                        .content(buildRequestBody(externalId, 175.0, 75.0, "MALE", null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.recommendedSize", notNullValue()))
                .andExpect(jsonPath("$.data.confidenceScore", greaterThan(0.0)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(true)))
                .andExpect(jsonPath("$.data.quality", notNullValue()))
                .andExpect(jsonPath("$.data.message", notNullValue()));
    }

    // -----------------------------------------------------------------------
    // Scenario 4: Valid API key + product with no size chart → HTTP 200, fallback
    // -----------------------------------------------------------------------

    @Test
    void scenario4_validKeyAndProductWithNoSizeChart_returns200WithFallback() throws Exception {
        String externalId = "shopify-no-chart-" + getUniqueSuffixFromApiKey();

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(API_KEY_HEADER, testApiKey)
                        .content(buildRequestBody(externalId, 175.0, 75.0, "MALE", null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(false)))
                .andExpect(jsonPath("$.data.recommendedSize", nullValue()))
                .andExpect(jsonPath("$.data.quality", is("NO_MATCH")));
    }

    // -----------------------------------------------------------------------
    // Scenario 5: Valid API key + product not found → HTTP 404
    // -----------------------------------------------------------------------

    @Test
    void scenario5_validKeyAndProductNotFound_returns404WithProductNotFoundCode() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(API_KEY_HEADER, testApiKey)
                        .content(buildRequestBody("shopify-nonexistent-99999", 175.0, 75.0, "MALE", null, false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    // -----------------------------------------------------------------------
    // Scenario 6: Valid API key + invalid body (heightCm = 0) → HTTP 400
    // -----------------------------------------------------------------------

    @Test
    void scenario6_validKeyAndInvalidBody_returns400WithValidationError() throws Exception {
        String externalId = "shopify-001-" + getUniqueSuffixFromApiKey();

        // heightCm = 0 fails @DecimalMin("50") validation
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(API_KEY_HEADER, testApiKey)
                        .content(buildRequestBody(externalId, 0.0, 75.0, "MALE", null, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("VALIDATION_ERROR")));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a JSON request body string for the recommendation endpoint.
     */
    private String buildRequestBody(
            String externalProductId,
            double heightCm,
            double weightKg,
            String gender,
            Integer age,
            boolean storeBodyData) throws Exception {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalProductId", externalProductId);
        body.put("heightCm", heightCm);
        body.put("weightKg", weightKg);
        if (gender != null) {
            body.put("gender", gender);
        }
        if (age != null) {
            body.put("age", age);
        }
        body.put("storeBodyData", storeBodyData);
        return objectMapper.writeValueAsString(body);
    }

    /**
     * Extracts the unique suffix from the test API key so we can reconstruct the
     * external product IDs that were inserted in @BeforeEach.
     * The key format is "it-key-{10-char suffix}".
     */
    private String getUniqueSuffixFromApiKey() {
        return testApiKey.substring("it-key-".length());
    }
}
