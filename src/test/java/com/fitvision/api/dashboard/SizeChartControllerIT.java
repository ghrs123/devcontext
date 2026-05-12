package com.fitvision.api.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.product.Product;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import com.fitvision.domain.store.Store;
import com.fitvision.testutil.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the dashboard size-chart endpoints.
 *
 * <p>
 * Base URL: {@code /api/dashboard/v1/size-charts/{productId}}
 *
 * <p>
 * All dashboard requests are authenticated using JWT in the Authorization header.
 * Test data is created in {@code @BeforeEach} and fully removed in
 * {@code @AfterEach}.
 */
@AutoConfigureMockMvc
class SizeChartControllerIT extends AbstractIntegrationTest {

         private static final String BASE_URL = "/api/dashboard/v1/size-charts";
         private static final String AUTHORIZATION_HEADER = "Authorization";
         private static final String BEARER_PREFIX = "Bearer ";
         private static final String WIDGET_URL = "/api/widget/v1/size-recommendation";
         private static final String API_KEY_HEADER = "X-FitVision-Key";

        // Standard valid CSV (3 data rows)
        private static final String CSV_HEADER = "size_label,chest_min,chest_max,waist_min,waist_max,hip_min,hip_max,height_min,height_max\n";
        private static final String CSV_ROWS = "S,85.0,90.0,65.0,70.0,88.0,93.0,160.0,170.0\n"
                        + "M,90.0,96.0,70.0,76.0,93.0,99.0,170.0,180.0\n"
                        + "L,96.0,102.0,76.0,82.0,99.0,105.0,180.0,190.0\n";
        private static final String VALID_CSV = CSV_HEADER + CSV_ROWS;

        // Test data identifiers
        private UUID storeId;
        private String secretKey;
        private String publicApiKey;
        private String jwtToken;
        private UUID brandId;
        private UUID productId;
        private String externalProductId;
        private UUID otherStoreId;
        private UUID otherProductId;

        @LocalServerPort
        private int port;

        @Autowired
        private MockMvc mockMvc;
        @Autowired
        private TestRestTemplate testRestTemplate;
        @Autowired
        private ObjectMapper objectMapper;
        @Autowired
        private StoreRepository storeRepository;
        @Autowired
        private BrandRepository brandRepository;
        @Autowired
        private ProductRepository productRepository;
        @Autowired
        private JwtService jwtService;
        @Autowired
        private PasswordEncoder passwordEncoder;
        @Autowired
        private JdbcTemplate jdbcTemplate;

        @BeforeEach
        void setUp() {
                String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                secretKey = "secret-" + suffix;
                publicApiKey = "pub-" + suffix;
                externalProductId = "dash-prod-" + suffix;

        // --- Main tenant store ---
        storeId = UUID.randomUUID();
        Store store = Store.builder()
                .id(storeId)
                .name("Dashboard IT Store")
                .email("dash-it-" + suffix + "@test.com")
                .plan("pro")
                .status("ACTIVE")
                .apiKeyPublic(publicApiKey)
                .apiKeySecret(secretKey)
                .passwordHash(passwordEncoder.encode("StrongPass#123"))
                .platform("shopify")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        storeRepository.save(store);
        jwtToken = jwtService.generateToken(storeId, store.getEmail());

        // --- Brand ---
        brandId = UUID.randomUUID();
        Brand brand = Brand.builder()
                .id(brandId)
                .tenantId(storeId)
                .name("Dashboard IT Brand")
                .slug("dash-brand-" + suffix)
                .source("store_uploaded")
                .createdAt(LocalDateTime.now())
                .build();
        brandRepository.save(brand);

        // --- Product belonging to main tenant ---
        productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setBrandId(brandId);
        product.setTenantId(storeId);
        product.setExternalProductId(externalProductId);
        product.setName("Dashboard IT Shirt");
        product.setCategory("tops");
        product.setGenderTarget("unisex");
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        // --- Second tenant (for cross-tenant isolation test) ---
        String suffix2 = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        otherStoreId = UUID.randomUUID();
        Store otherStore = Store.builder()
                .id(otherStoreId)
                .name("Other IT Store")
                .email("other-it-" + suffix2 + "@test.com")
                .plan("starter")
                .status("ACTIVE")
                .apiKeyPublic("pub2-" + suffix2)
                .apiKeySecret("secret2-" + suffix2)
                .passwordHash(passwordEncoder.encode("StrongPass#123"))
                .platform("woocommerce")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        storeRepository.save(otherStore);

        otherProductId = UUID.randomUUID();
        Product otherProduct = new Product();
        otherProduct.setId(otherProductId);
        otherProduct.setBrandId(brandId); // brand is shared (no FK cross-tenant constraint)
        otherProduct.setTenantId(otherStoreId);
        otherProduct.setExternalProductId("other-prod-" + suffix2);
        otherProduct.setName("Other IT Shirt");
        otherProduct.setCategory("tops");
        otherProduct.setGenderTarget("unisex");
        otherProduct.setCreatedAt(LocalDateTime.now());
        otherProduct.setUpdatedAt(LocalDateTime.now());
        productRepository.save(otherProduct);
    }

        @AfterEach
        void tearDown() {
                // Delete in FK-safe order
                if (storeId != null) {
                        jdbcTemplate.update("DELETE FROM recommendation_requests WHERE tenant_id = ?", storeId);
                }
                if (otherStoreId != null) {
                        jdbcTemplate.update("DELETE FROM recommendation_requests WHERE tenant_id = ?", otherStoreId);
                }
                jdbcTemplate.update("DELETE FROM size_entries WHERE size_chart_id IN " +
                                "(SELECT id FROM size_charts WHERE product_id = ? OR product_id = ?)",
                                productId, otherProductId);
                jdbcTemplate.update("DELETE FROM size_charts WHERE product_id = ? OR product_id = ?",
                                productId, otherProductId);
                if (productId != null)
                        productRepository.deleteById(productId);
                if (otherProductId != null)
                        productRepository.deleteById(otherProductId);
                if (brandId != null)
                        brandRepository.deleteById(brandId);
                if (storeId != null)
                        storeRepository.deleteById(storeId);
                if (otherStoreId != null)
                        storeRepository.deleteById(otherStoreId);
        }

        // -----------------------------------------------------------------------
        // Scenario 1: POST /{productId}/upload valid CSV → 200, version=1, entries>0
        // -----------------------------------------------------------------------

        @Test
        void scenario1_uploadValidCsv_returns200WithVersion1AndEntries() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));

                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.version", is(1)))
                                .andExpect(jsonPath("$.data.entriesSaved", greaterThan(0)))
                                .andExpect(jsonPath("$.data.sizeChartId", notNullValue()));
        }

        // -----------------------------------------------------------------------
        // Scenario 2: Second CSV upload → version=2, first chart deactivated
        // -----------------------------------------------------------------------

        @Test
        void scenario2_secondUpload_returnsVersion2AndDeactivatesPrevious() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));

                // First upload
                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.version", is(1)));

                // Second upload
                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.version", is(2)));

                // Validate only one active version remains for this product.
                Integer activeCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM size_charts WHERE product_id = ? AND active = true",
                                Integer.class,
                                productId);
                assertThat(activeCount).isEqualTo(1);
        }

        // -----------------------------------------------------------------------
        // Scenario 2.1: POST /upload valid XLSX → 200, version=1, entries>0
        // -----------------------------------------------------------------------

        @Test
        void scenario21_uploadValidXlsx_returns200WithVersion1AndEntries() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                TestDataBuilder.buildValidExcel(3));

                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.version", is(1)))
                                .andExpect(jsonPath("$.data.entriesSaved", greaterThan(0)));
        }

        // -----------------------------------------------------------------------
        // Scenario 3: Invalid JWT → 401 UNAUTHORIZED
        // -----------------------------------------------------------------------

        @Test
        void scenario3_invalidJwt_returns401() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));

                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken("totally-wrong-jwt-99999")))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.success", is(false)))
                                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
        }

        // -----------------------------------------------------------------------
        // Scenario 3.1: POST /upload file > 2MB → 400 VALIDATION_ERROR
        // -----------------------------------------------------------------------

        @Test
        void scenario31_uploadFileTooLarge_returns400() {
                byte[] tooLargePayload = TestDataBuilder.buildLargeFileOver2Mb();

                ByteArrayResource resource = new ByteArrayResource(tooLargePayload) {
                        @Override
                        public String getFilename() {
                                return "too-large.csv";
                        }
                };

                MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
                multipartBody.add("file", resource);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.add(AUTHORIZATION_HEADER, bearerToken(jwtToken));

                HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(multipartBody, headers);
                ResponseEntity<String> response = testRestTemplate.exchange(
                                "http://localhost:" + port + BASE_URL + "/" + productId + "/upload",
                                HttpMethod.POST,
                                request,
                                String.class);

                assertThat(response.getStatusCode().value()).isEqualTo(400);
                assertThat(response.getBody()).contains("VALIDATION_ERROR");
        }

        // -----------------------------------------------------------------------
        // Scenario 4: POST /{productId}/manual valid entries → 200
        // -----------------------------------------------------------------------

        @Test
        void scenario4_manualUpload_returns200() throws Exception {
                List<SizeEntryData> entries = List.of(
                                new SizeEntryData("S", 85.0, 90.0, 65.0, 70.0, 88.0, 93.0, 160.0, 170.0),
                                new SizeEntryData("M", 90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0));

                mockMvc.perform(post(BASE_URL + "/" + productId + "/manual")
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(entries)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.entriesSaved", is(2)));
        }

        // -----------------------------------------------------------------------
        // Scenario 5: GET /{productId}/active after upload → 200 with entries
        // -----------------------------------------------------------------------

        @Test
        void scenario5_getActiveAfterUpload_returns200WithEntries() throws Exception {
                // Upload first
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));
                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk());

                // Then GET active
                mockMvc.perform(get(BASE_URL + "/" + productId + "/active")
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.length()", greaterThan(0)));
        }

        // -----------------------------------------------------------------------
        // Scenario 6: DELETE /{productId}/active → 204; subsequent GET returns empty
        // list
        // -----------------------------------------------------------------------

        @Test
        void scenario6_deleteActive_returns204AndSubsequentGetReturnsEmpty() throws Exception {
                // Upload first
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));
                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk());

                // DELETE active
                mockMvc.perform(delete(BASE_URL + "/" + productId + "/active")
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isNoContent());

                // GET should return empty list now
                mockMvc.perform(get(BASE_URL + "/" + productId + "/active")
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.length()", is(0)));
        }

        // -----------------------------------------------------------------------
        // Scenario 7: GET active for product of different tenant → 404
        // PRODUCT_NOT_FOUND
        // -----------------------------------------------------------------------

        @Test
        void scenario7_getActiveForOtherTenantProduct_returns404() throws Exception {
                // Main tenant JWT trying to access other tenant's product
                mockMvc.perform(get(BASE_URL + "/" + otherProductId + "/active")
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.success", is(false)))
                                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
        }

        // -----------------------------------------------------------------------
        // Scenario 8: Upload then widget recommendation returns a valid size
        // -----------------------------------------------------------------------

        @Test
        void scenario8_afterUpload_widgetRecommendationReturnsValidSize() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file", "sizes.csv", "text/csv",
                                VALID_CSV.getBytes(StandardCharsets.UTF_8));

                mockMvc.perform(multipart(BASE_URL + "/" + productId + "/upload")
                                .file(file)
                                .header(AUTHORIZATION_HEADER, bearerToken(jwtToken)))
                                .andExpect(status().isOk());

                mockMvc.perform(post(WIDGET_URL)
                                .header(API_KEY_HEADER, publicApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(buildWidgetRequestBody(externalProductId, 175.0, 75.0, "MALE", null, false)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success", is(true)))
                                .andExpect(jsonPath("$.data.recommendedSize", notNullValue()))
                                .andExpect(jsonPath("$.data.confidenceScore", greaterThan(0.0)));
        }

        private String buildWidgetRequestBody(
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

        private static String bearerToken(String token) {
                return BEARER_PREFIX + token;
        }
}
