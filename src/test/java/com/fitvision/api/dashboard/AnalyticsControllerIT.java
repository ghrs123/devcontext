package com.fitvision.api.dashboard;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AnalyticsControllerIT extends AbstractIntegrationTest {

    private static final String ANALYTICS_BASE_URL = "/api/dashboard/v1/analytics";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private UUID storeId;
    private UUID productOneId;
    private UUID productTwoId;
    private String jwtToken;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        String suffix = uniqueSuffix();

        storeId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("Analytics Store")
                .email("analytics-" + suffix + "@test.com")
                .plan("FREE")
                .status("ACTIVE")
                .apiKeyPublic("pub-analytics-" + suffix)
                .apiKeySecret("sec-analytics-" + suffix)
                .passwordHash("$2a$12$abcdefghijklmnopqrstuv")
                .platform("shopify")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        storeRepository.save(store);
        jwtToken = jwtService.generateToken(storeId, store.getEmail());

        UUID brandId = UUID.randomUUID();
        brandRepository.save(Brand.builder()
                .id(brandId)
                .tenantId(storeId)
                .name("Analytics Brand")
                .slug("analytics-brand-" + suffix)
                .source("store_uploaded")
                .createdAt(LocalDateTime.now())
                .build());

        productOneId = UUID.randomUUID();
        Product productOne = new Product();
        productOne.setId(productOneId);
        productOne.setBrandId(brandId);
        productOne.setTenantId(storeId);
        productOne.setExternalProductId("analytics-prod-1-" + suffix);
        productOne.setName("Analytics Product 1");
        productOne.setCategory("tops");
        productOne.setGenderTarget("unisex");
        productOne.setCreatedAt(LocalDateTime.now());
        productOne.setUpdatedAt(LocalDateTime.now());
        productRepository.save(productOne);

        productTwoId = UUID.randomUUID();
        Product productTwo = new Product();
        productTwo.setId(productTwoId);
        productTwo.setBrandId(brandId);
        productTwo.setTenantId(storeId);
        productTwo.setExternalProductId("analytics-prod-2-" + suffix);
        productTwo.setName("Analytics Product 2");
        productTwo.setCategory("tops");
        productTwo.setGenderTarget("unisex");
        productTwo.setCreatedAt(LocalDateTime.now());
        productTwo.setUpdatedAt(LocalDateTime.now());
        productRepository.save(productTwo);

        insertRecommendation(productOneId, "M", BigDecimal.valueOf(1.0), LocalDateTime.now().minusDays(5));
        insertRecommendation(productOneId, "L", BigDecimal.valueOf(0.7), LocalDateTime.now().minusDays(2));
        insertRecommendation(productTwoId, "NO_MATCH", BigDecimal.ZERO, LocalDateTime.now().minusDays(50));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recommendation_requests");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM brands");
        jdbcTemplate.update("DELETE FROM stores");
    }

    @Test
    void scenario1_summaryAfterRecommendations_returnsExpectedCounts() throws Exception {
        mockMvc.perform(get(ANALYTICS_BASE_URL + "/summary")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalRecommendations", is(3)))
                .andExpect(jsonPath("$.data.recommendationsLast30Days", is(2)))
                .andExpect(jsonPath("$.data.averageConfidenceScore", closeTo(0.56, 0.2)))
                .andExpect(jsonPath("$.data.qualityDistribution.EXACT", is(1)))
                .andExpect(jsonPath("$.data.qualityDistribution.PARTIAL", is(1)))
                .andExpect(jsonPath("$.data.qualityDistribution.CLOSEST", is(0)))
                .andExpect(jsonPath("$.data.qualityDistribution.NO_MATCH", is(1)))
                .andExpect(jsonPath("$.data.topProducts.length()", greaterThan(0)));
    }

    @Test
    void scenario2_recommendationsPaginated_returnsCorrectPageSizeAndTotal() throws Exception {
        mockMvc.perform(get(ANALYTICS_BASE_URL + "/recommendations?page=0&size=2")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.size", is(2)))
                .andExpect(jsonPath("$.data.number", is(0)))
                .andExpect(jsonPath("$.data.totalElements", is(3)))
                .andExpect(jsonPath("$.data.content.length()", is(2)));
    }

    private void insertRecommendation(UUID productId,
                                      String recommendedSize,
                                      BigDecimal confidenceScore,
                                      LocalDateTime createdAt) {
        jdbcTemplate.update(
                "INSERT INTO recommendation_requests " +
                        "(id, tenant_id, product_id, height_cm, weight_kg, gender, age, recommended_size, confidence_score, body_measurements_stored, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                storeId,
                productId,
                BigDecimal.valueOf(175.0),
                BigDecimal.valueOf(75.0),
                "MALE",
                30,
                recommendedSize,
                confidenceScore,
                false,
                Timestamp.valueOf(createdAt));
    }

    private String bearer(String token) {
        return BEARER_PREFIX + token;
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
