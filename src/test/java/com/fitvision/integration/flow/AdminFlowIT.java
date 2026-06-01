package com.fitvision.integration.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.billing.Plan;
import com.fitvision.domain.sizechart.SizeEntryData;

@AutoConfigureMockMvc
class AdminFlowIT extends AbstractIntegrationTest {

        private static final String BOOTSTRAP_TOKEN_HEADER = "X-Bootstrap-Token";
        private static final String BOOTSTRAP_TOKEN_VALUE = "test-bootstrap-token";

    private final List<UUID> storeIdsToCleanup = new ArrayList<>();
    private UUID globalBrandId;

    @AfterEach
    void tearDown() {
        storeIdsToCleanup.forEach(this::cleanupStoreData);
        storeIdsToCleanup.clear();
        if (globalBrandId != null) {
            cleanupGlobalBrand(globalBrandId);
            globalBrandId = null;
        }
    }

    @Test
    void adminSeed_firstCall_createsAdmin() throws Exception {
        deleteAllAdmins();

        String email = "seed-admin-" + testUniqueSuffix() + "@fitvision.io";
        MvcResult result = mockMvc.perform(post(ADMIN_SEED_URL)
                        .header(BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", "AdminSeed#123",
                                "name", "Seed Admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();

        mockMvc.perform(get(ADMIN_METRICS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void adminSeed_secondCall_returns409() throws Exception {
        deleteAllAdmins();

        Map<String, String> seedBody = Map.of(
                "email", "seed-twice-" + testUniqueSuffix() + "@fitvision.io",
                "password", "AdminSeed#123",
                "name", "Seed Admin Twice");

        mockMvc.perform(post(ADMIN_SEED_URL)
                        .header(BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seedBody)))
                .andExpect(status().isOk());

        mockMvc.perform(post(ADMIN_SEED_URL)
                        .header(BOOTSTRAP_TOKEN_HEADER, BOOTSTRAP_TOKEN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(seedBody)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.message", is("Bootstrap already used")));
    }

    @Test
    void adminMetrics_afterStoresAndRecommendations_returnsCorrectData() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        StoreSession storeA = registerAndLogin("Metrics Store A", "metrics-a-" + suffix + "@test.com", "StrongPass#123");
        StoreSession storeB = registerAndLogin("Metrics Store B", "metrics-b-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(storeA.storeId());
        storeIdsToCleanup.add(storeB.storeId());

        UUID brandId = createBrand(storeA.jwt(), "Metrics Brand");
        String externalId = "metrics-prod-" + suffix;
        UUID productId = createProductViaApi(storeA.jwt(), externalId, "Metrics Product", brandId);
        uploadManualSizeChart(storeA.jwt(), productId, List.of(
                new SizeEntryData("M", 115.0, 125.0, null, null, null, null, null, null)));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                            .header(API_KEY_HEADER, storeA.apiKeyPublic())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(buildWidgetRecommendationBody(externalId, 175.0, 75.0, "MALE")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get(ADMIN_METRICS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStores", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.totalRecommendations", greaterThanOrEqualTo(5)));
    }

    @Test
    void adminStores_list_returnsAllStores() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        StoreSession s1 = registerAndLogin("List Store 1", "list-s1-" + suffix + "@test.com", "StrongPass#123");
        StoreSession s2 = registerAndLogin("List Store 2", "list-s2-" + suffix + "@test.com", "StrongPass#123");
        StoreSession s3 = registerAndLogin("List Store 3", "list-s3-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(s1.storeId());
        storeIdsToCleanup.add(s2.storeId());
        storeIdsToCleanup.add(s3.storeId());

        MvcResult listResult = mockMvc.perform(get(ADMIN_STORES_URL)
                        .param("status", "ALL")
                        .param("size", "100")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt)))
                .andExpect(status().isOk())
                .andReturn();

        String listJson = listResult.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(listJson.contains("list-s1-" + suffix + "@test.com"));
        org.junit.jupiter.api.Assertions.assertTrue(listJson.contains("list-s2-" + suffix + "@test.com"));
        org.junit.jupiter.api.Assertions.assertTrue(listJson.contains("list-s3-" + suffix + "@test.com"));
    }

    @Test
    void adminDeactivateStore_widgetReturns401() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        StoreSession store = registerAndLogin("Deactivate Store", "deact-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(store.storeId());

        UUID brandId = createBrand(store.jwt(), "Deact Brand");
        String externalId = "deact-prod-" + suffix;
        UUID productId = createProductViaApi(store.jwt(), externalId, "Deact Product", brandId);
        uploadManualSizeChart(store.jwt(), productId, List.of(
                new SizeEntryData("M", 115.0, 125.0, null, null, null, null, null, null)));

        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + store.storeId() + "/status")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, store.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(externalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    @Test
    void adminReactivateStore_widgetWorksAgain() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        StoreSession store = registerAndLogin("Reactivate Store", "react-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(store.storeId());

        UUID brandId = createBrand(store.jwt(), "React Brand");
        String externalId = "react-prod-" + suffix;
        UUID productId = createProductViaApi(store.jwt(), externalId, "React Product", brandId);
        uploadManualSizeChart(store.jwt(), productId, List.of(
                new SizeEntryData("M", 115.0, 125.0, null, null, null, null, null, null)));

        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + store.storeId() + "/status")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + store.storeId() + "/status")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, store.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(externalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void adminOverridePlan_changesPlanDirectly() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        StoreSession store = registerAndLogin("Override Store", "override-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(store.storeId());

        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + store.storeId() + "/plan")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan", "PRO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(BILLING_STATUS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(store.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan", is("PRO")))
                .andExpect(jsonPath("$.data.productsLimit", is(Plan.PRO.getMaxProducts())));
    }

    @Test
    void adminGlobalBrand_createAndUploadSizeChart_availableToAllStores() throws Exception {
        String adminJwt = ensureAdminJwt();
        String suffix = testUniqueSuffix();
        String brandName = "Zara Global " + suffix;

        MvcResult brandResult = mockMvc.perform(post(ADMIN_BRANDS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", brandName))))
                .andExpect(status().isOk())
                .andReturn();

        globalBrandId = UUID.fromString(
                objectMapper.readTree(brandResult.getResponse().getContentAsString())
                        .path("data").path("id").asText());

        MockMultipartFile file = new MockMultipartFile(
                "file", "size-chart-tops.csv", "text/csv", loadTestCsvBytes());
        mockMvc.perform(multipart(ADMIN_BRANDS_URL + "/" + globalBrandId + "/size-charts/upload")
                        .file(file)
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt)))
                .andExpect(status().isOk());

        StoreSession store = registerAndLogin("Global Brand Store", "global-brand-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(store.storeId());

        mockMvc.perform(get(BRANDS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(store.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].name", hasItem(brandName)));
    }

    @Test
    void adminEndpoints_storeJwt_returns403() throws Exception {
        String suffix = testUniqueSuffix();
        StoreSession store = registerAndLogin("Store JWT Admin", "store-jwt-" + suffix + "@test.com", "StrongPass#123");
        storeIdsToCleanup.add(store.storeId());

        mockMvc.perform(get(ADMIN_METRICS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(store.jwt())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
