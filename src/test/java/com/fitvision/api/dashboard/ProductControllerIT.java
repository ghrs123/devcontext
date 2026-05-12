package com.fitvision.api.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductControllerIT extends AbstractIntegrationTest {

    private static final String PRODUCTS_URL = "/api/dashboard/v1/products";
    private static final String SIZE_CHARTS_URL = "/api/dashboard/v1/size-charts";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String CSV_CONTENT =
            "size_label,chest_min,chest_max,waist_min,waist_max,hip_min,hip_max,height_min,height_max\n" +
            "M,90.0,98.0,74.0,82.0,92.0,100.0,170.0,180.0\n";

    private UUID tenantOneId;
    private UUID tenantTwoId;
    private UUID tenantOneBrandId;
    private UUID tenantTwoBrandId;
    private String tenantOneJwt;
    private String tenantTwoJwt;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        String suffix1 = uniqueSuffix();
        String suffix2 = uniqueSuffix();

        tenantOneId = UUID.randomUUID();
        tenantTwoId = UUID.randomUUID();

        Store storeOne = Store.builder()
                .id(tenantOneId)
                .name("Tenant One")
                .email("tenant-one-" + suffix1 + "@test.com")
                .plan("FREE")
                .status("ACTIVE")
                .apiKeyPublic("pub-one-" + suffix1)
                .apiKeySecret("sec-one-" + suffix1)
                .passwordHash("$2a$12$abcdefghijklmnopqrstuv")
                .platform("shopify")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Store storeTwo = Store.builder()
                .id(tenantTwoId)
                .name("Tenant Two")
                .email("tenant-two-" + suffix2 + "@test.com")
                .plan("FREE")
                .status("ACTIVE")
                .apiKeyPublic("pub-two-" + suffix2)
                .apiKeySecret("sec-two-" + suffix2)
                .passwordHash("$2a$12$abcdefghijklmnopqrstuv")
                .platform("woocommerce")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeRepository.save(storeOne);
        storeRepository.save(storeTwo);

        tenantOneJwt = jwtService.generateToken(tenantOneId, storeOne.getEmail());
        tenantTwoJwt = jwtService.generateToken(tenantTwoId, storeTwo.getEmail());

        tenantOneBrandId = UUID.randomUUID();
        tenantTwoBrandId = UUID.randomUUID();

        brandRepository.save(Brand.builder()
                .id(tenantOneBrandId)
                .tenantId(tenantOneId)
                .name("Brand One")
                .slug("brand-one-" + suffix1)
                .source("store_uploaded")
                .createdAt(LocalDateTime.now())
                .build());

        brandRepository.save(Brand.builder()
                .id(tenantTwoBrandId)
                .tenantId(tenantTwoId)
                .name("Brand Two")
                .slug("brand-two-" + suffix2)
                .source("store_uploaded")
                .createdAt(LocalDateTime.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recommendation_requests");
        jdbcTemplate.update("DELETE FROM size_entries");
        jdbcTemplate.update("DELETE FROM size_charts");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM brands");
        jdbcTemplate.update("DELETE FROM stores");
    }

    @Test
    void scenario1_fullCrudCycle_createGetUpdateDeleteThenGet404() throws Exception {
        UUID productId = createProduct(tenantOneJwt, "ext-" + uniqueSuffix(), "Original Name", tenantOneBrandId);

        mockMvc.perform(get(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(productId.toString())))
                .andExpect(jsonPath("$.data.name", is("Original Name")));

        mockMvc.perform(put(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest("ext-updated", "Updated Name", "tops", "unisex", tenantOneBrandId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated Name")));

        mockMvc.perform(delete(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void scenario2_getList_hasSizeChartFalseBeforeUploadAndTrueAfterUpload() throws Exception {
        UUID productId = createProduct(tenantOneJwt, "ext-chart-" + uniqueSuffix(), "Chart Product", tenantOneBrandId);

        mockMvc.perform(get(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(productId.toString())))
                .andExpect(jsonPath("$.data[0].hasSizeChart", is(false)));

        MockMultipartFile file = new MockMultipartFile(
                "file", "sizes.csv", "text/csv", CSV_CONTENT.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(SIZE_CHARTS_URL + "/" + productId + "/upload")
                        .file(file)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sizeChartId", notNullValue()));

        mockMvc.perform(get(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(productId.toString())))
                .andExpect(jsonPath("$.data[0].hasSizeChart", is(true)));
    }

    @Test
    void scenario3_deletedProductDisappearsFromList() throws Exception {
        UUID productId = createProduct(tenantOneJwt, "ext-delete-" + uniqueSuffix(), "Delete Product", tenantOneBrandId);

        mockMvc.perform(delete(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, bearer(tenantOneJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    void scenario4_crossTenantAccessOtherStoreProduct_returns404() throws Exception {
        UUID productId = createProduct(tenantOneJwt, "ext-cross-" + uniqueSuffix(), "Cross Product", tenantOneBrandId);

        mockMvc.perform(get(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, bearer(tenantTwoJwt)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    private UUID createProduct(String jwtToken,
                               String externalProductId,
                               String name,
                               UUID brandId) throws Exception {
        MvcResult result = mockMvc.perform(post(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest(externalProductId, name, "tops", "unisex", brandId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(root.path("data").path("id").asText());
    }

    private String buildProductRequest(String externalProductId,
                                       String name,
                                       String category,
                                       String genderTarget,
                                       UUID brandId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalProductId", externalProductId);
        body.put("name", name);
        body.put("category", category);
        body.put("genderTarget", genderTarget);
        body.put("brandId", brandId);
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return BEARER_PREFIX + token;
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
