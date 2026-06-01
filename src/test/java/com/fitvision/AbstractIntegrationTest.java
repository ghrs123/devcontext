package com.fitvision;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.api.admin.AdminSeedController;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.domain.store.Store;
import com.fitvision.domain.store.StoreRole;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;

/**
 * Base class for all FitVision integration tests.
 *
 * <p>Starts a single PostgreSQL Testcontainer (shared across all subclasses via the
 * static container field). Flyway migrations run automatically on the first
 * application context startup and are not repeated on subsequent tests in the same
 * JVM run.
 *
 * <p>Subclasses must manage test data isolation themselves — typically via
 * {@code @BeforeEach} setup and {@code @AfterEach} teardown.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String AUTH_URL = "/api/dashboard/v1/auth";
    protected static final String STORE_PROFILE_URL = "/api/dashboard/v1/store/profile";
    protected static final String PRODUCTS_URL = "/api/dashboard/v1/products";
    protected static final String BRANDS_URL = "/api/dashboard/v1/brands";
    protected static final String SIZE_CHARTS_URL = "/api/dashboard/v1/size-charts";
    protected static final String WIDGET_RECOMMENDATION_URL = "/api/widget/v1/size-recommendation";
    protected static final String ANALYTICS_URL = "/api/dashboard/v1/analytics";
    protected static final String ADMIN_STORES_URL = "/api/admin/v1/stores";
    protected static final String ADMIN_SEED_URL = "/api/admin/seed";
    protected static final String ADMIN_METRICS_URL = "/api/admin/v1/metrics";
    protected static final String ADMIN_BRANDS_URL = "/api/admin/v1/brands";
    protected static final String BILLING_STATUS_URL = "/api/dashboard/v1/billing/status";
    protected static final String BILLING_CHECKOUT_URL = "/api/dashboard/v1/billing/checkout";
    protected static final String STRIPE_WEBHOOK_URL = "/api/billing/webhooks";
    protected static final String SHOPIFY_CONNECT_URL = "/api/shopify/connect";
    protected static final String SHOPIFY_STATUS_URL = "/api/shopify/status";
    protected static final String SHOPIFY_SECRET_HEADER = "X-FitVision-Shopify-Secret";
    protected static final String AUTHORIZATION_HEADER = "Authorization";
    protected static final String BEARER_PREFIX = "Bearer ";
    protected static final String API_KEY_HEADER = "X-FitVision-Key";
    protected static final String TEST_CSV_PATH = "test-data/size-chart-tops.csv";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("fitvision_test")
                    .withUsername("fitvision")
                    .withPassword("fitvision");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected StoreRepository storeRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected AdminSeedController adminSeedController;

    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected record StoreSession(
            UUID storeId,
            String jwt,
            String apiKeyPublic,
            String email,
            String password,
            String name
    ) {}

    protected String testUniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    protected String testBearer(String token) {
        return BEARER_PREFIX + token;
    }

    protected StoreSession registerAndLogin(String storeName, String email, String password) throws Exception {
        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest(storeName, email, password, "shopify")))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String jwt = loginBody.path("data").path("accessToken").asText();
        String apiKey = loginBody.path("data").path("apiKeyPublic").asText();

        MvcResult profileResult = mockMvc.perform(get(STORE_PROFILE_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(jwt)))
                .andExpect(status().isOk())
                .andReturn();

        UUID storeId = UUID.fromString(
                objectMapper.readTree(profileResult.getResponse().getContentAsString())
                        .path("data").path("id").asText());

        return new StoreSession(storeId, jwt, apiKey, email, password, storeName);
    }

    protected UUID createBrand(String jwt, String brandName) throws Exception {
        Map<String, Object> body = Map.of("name", brandName);
        MvcResult result = mockMvc.perform(post(BRANDS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("id").asText());
    }

    protected UUID createProductViaApi(String jwt, String externalProductId, String name, UUID brandId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalProductId", externalProductId);
        body.put("name", name);
        body.put("category", "tops");
        body.put("genderTarget", "male");
        if (brandId != null) {
            body.put("brandId", brandId.toString());
        }

        MvcResult result = mockMvc.perform(post(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("id").asText());
    }

    protected byte[] loadTestCsvBytes() throws Exception {
        return new ClassPathResource(TEST_CSV_PATH).getContentAsByteArray();
    }

    protected void uploadTestCsv(String jwt, UUID productId) throws Exception {
        uploadCsv(jwt, productId, loadTestCsvBytes(), "size-chart-tops.csv");
    }

    protected void uploadCsv(String jwt, UUID productId, byte[] csvBytes, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/csv", csvBytes);
        mockMvc.perform(multipart(SIZE_CHARTS_URL + "/" + productId + "/upload")
                        .file(file)
                        .header(AUTHORIZATION_HEADER, testBearer(jwt)))
                .andExpect(status().isOk());
    }

    protected void uploadManualSizeChart(String jwt, UUID productId, List<SizeEntryData> entries) throws Exception {
        mockMvc.perform(post(SIZE_CHARTS_URL + "/" + productId + "/manual")
                        .header(AUTHORIZATION_HEADER, testBearer(jwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(entries)))
                .andExpect(status().isOk());
    }

    protected String buildWidgetRecommendationBody(String externalProductId,
                                                   double heightCm,
                                                   double weightKg,
                                                   String gender) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalProductId", externalProductId);
        body.put("heightCm", heightCm);
        body.put("weightKg", weightKg);
        body.put("gender", gender);
        body.put("storeBodyData", false);
        return objectMapper.writeValueAsString(body);
    }

    protected String ensureAdminJwt() {
        return storeRepository.findFirstByRoleAndStatus(StoreRole.ADMIN.name(), "ACTIVE")
                .map(admin -> jwtService.generateToken(admin.getId(), admin.getEmail(), StoreRole.ADMIN.name()))
                .orElseGet(this::createAdminStoreAndJwt);
    }

    private String createAdminStoreAndJwt() {
        String suffix = testUniqueSuffix();
        String email = "admin-flow-" + suffix + "@test.com";
        Store admin = Store.builder()
                .id(UUID.randomUUID())
                .name("Flow IT Admin")
                .email(email)
                .plan("ADMIN")
                .status("ACTIVE")
                .apiKeyPublic("admin-pub-" + suffix)
                .apiKeySecret("admin-sec-" + suffix)
                .passwordHash(passwordEncoder.encode("AdminPass#123"))
                .platform("admin")
                .subscriptionStatus("active")
                .role(StoreRole.ADMIN.name())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        Store saved = storeRepository.save(admin);
        return jwtService.generateToken(saved.getId(), saved.getEmail(), StoreRole.ADMIN.name());
    }

    protected void deleteAllAdmins() {
        List<UUID> adminIds = jdbcTemplate.queryForList(
                "SELECT id FROM stores WHERE role = 'ADMIN'", UUID.class);
        for (UUID adminId : adminIds) {
            cleanupStoreData(adminId);
        }
        jdbcTemplate.update("DELETE FROM stores WHERE role = 'ADMIN'");
        adminSeedController.resetBootstrapState();
    }

    protected void cleanupShopifyStore(UUID storeId) {
        if (storeId == null) {
            return;
        }
        cleanupStoreData(storeId);
    }

    protected void cleanupGlobalBrand(UUID brandId) {
        if (brandId == null) {
            return;
        }
        jdbcTemplate.update(
                "DELETE FROM size_entries WHERE size_chart_id IN "
                        + "(SELECT sc.id FROM size_charts sc JOIN products p ON sc.product_id = p.id WHERE p.brand_id = ?)",
                brandId);
        jdbcTemplate.update(
                "DELETE FROM size_charts WHERE product_id IN (SELECT id FROM products WHERE brand_id = ?)",
                brandId);
        jdbcTemplate.update("DELETE FROM products WHERE brand_id = ?", brandId);
        jdbcTemplate.update("DELETE FROM brands WHERE id = ?", brandId);
    }

    protected void cleanupStoreData(UUID storeId) {
        if (storeId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM recommendation_requests WHERE tenant_id = ?", storeId);
        jdbcTemplate.update(
                "DELETE FROM size_entries WHERE size_chart_id IN "
                        + "(SELECT sc.id FROM size_charts sc JOIN products p ON sc.product_id = p.id WHERE p.tenant_id = ?)",
                storeId);
        jdbcTemplate.update(
                "DELETE FROM size_charts WHERE product_id IN (SELECT id FROM products WHERE tenant_id = ?)",
                storeId);
        jdbcTemplate.update("DELETE FROM products WHERE tenant_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM brands WHERE tenant_id = ?", storeId);
        jdbcTemplate.update("DELETE FROM stores WHERE id = ?", storeId);
    }

    private String buildRegisterRequest(String name, String email, String password, String platform) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        body.put("platform", platform);
        return objectMapper.writeValueAsString(body);
    }

    private String buildLoginRequest(String email, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return objectMapper.writeValueAsString(body);
    }
}
