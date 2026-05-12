package com.fitvision.api.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StoreControllerIT extends AbstractIntegrationTest {

    private static final String STORE_BASE_URL = "/api/dashboard/v1/store";
    private static final String WIDGET_URL = "/api/widget/v1/size-recommendation";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_HEADER = "X-FitVision-Key";

    private UUID storeId;
    private String jwtToken;
    private String currentApiKey;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        String suffix = uniqueSuffix();
        storeId = UUID.randomUUID();
        currentApiKey = "pub-" + suffix;
        String email = "store-it-" + suffix + "@test.com";

        Store store = Store.builder()
                .id(storeId)
                .name("Store IT")
            .email(email)
                .plan("FREE")
                .status("ACTIVE")
                .apiKeyPublic(currentApiKey)
                .apiKeySecret("secret-" + suffix)
                .passwordHash("$2a$12$abcdefghijklmnopqrstuv")
                .platform("shopify")
                .subscriptionStatus("active")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        storeRepository.save(store);
    jwtToken = jwtService.generateToken(storeId, email);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM recommendation_requests");
        jdbcTemplate.update("DELETE FROM stores");
    }

    @Test
    void scenario1_getProfileWithoutJwt_returns401() throws Exception {
        mockMvc.perform(get(STORE_BASE_URL + "/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHORIZED")));
    }

    @Test
    void scenario2_getProfileWithJwt_returns200() throws Exception {
        mockMvc.perform(get(STORE_BASE_URL + "/profile")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(storeId.toString())))
                .andExpect(jsonPath("$.data.email", notNullValue()));
    }

    @Test
    void scenario3_patchProfile_updatesName() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Updated Store Name");

        mockMvc.perform(patch(STORE_BASE_URL + "/profile")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is("Updated Store Name")));
    }

    @Test
    void scenario4_getApiKeys_returnsBothKeys() throws Exception {
        mockMvc.perform(get(STORE_BASE_URL + "/api-keys")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.apiKeyPublic", notNullValue()))
                .andExpect(jsonPath("$.data.apiKeySecret", notNullValue()));
    }

    @Test
    void scenario5_regenerateApiKeys_oldWidgetCallsReturn401() throws Exception {
        mockMvc.perform(post(STORE_BASE_URL + "/api-keys/regenerate")
                        .header(AUTHORIZATION_HEADER, bearer(jwtToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.apiKeyPublic", not(is(currentApiKey))));

        mockMvc.perform(post(WIDGET_URL)
                        .header(API_KEY_HEADER, currentApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRequestBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    private String bearer(String token) {
        return BEARER_PREFIX + token;
    }

    private String buildWidgetRequestBody() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalProductId", "non-existent-product");
        body.put("heightCm", 175.0);
        body.put("weightKg", 75.0);
        body.put("gender", "MALE");
        body.put("storeBodyData", false);
        return objectMapper.writeValueAsString(body);
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
