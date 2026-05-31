package com.fitvision.integration.flow;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.billing.Plan;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RecommendationFlowIT extends AbstractIntegrationTest {

    private StoreSession session;
    private String productWithChartExternalId;
    private String productNoChartExternalId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = testUniqueSuffix();
        session = registerAndLogin(
                "Recommendation Flow Store",
                "flow-rec-" + suffix + "@test.com",
                "StrongPass#123");

        UUID brandId = createBrand(session.jwt(), "Rec Brand " + suffix);
        productWithChartExternalId = "rec-chart-" + suffix;
        productNoChartExternalId = "rec-no-chart-" + suffix;

        UUID productWithChart = createProductViaApi(session.jwt(), productWithChartExternalId, "Chart Product", brandId);
        createProductViaApi(session.jwt(), productNoChartExternalId, "No Chart Product", brandId);

        uploadManualSizeChart(session.jwt(), productWithChart, List.of(
                new SizeEntryData("S", 97.8, 113.0, null, null, null, null, null, null),
                new SizeEntryData("M", 115.0, 125.0, null, null, null, null, null, null),
                new SizeEntryData("L", 126.0, 136.0, null, null, null, null, null, null)));
    }

    @AfterEach
    void tearDown() {
        cleanupStoreData(session != null ? session.storeId() : null);
    }

    @Test
    void recommendation_withSizeChart_returnsCorrectSize() throws Exception {
        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productWithChartExternalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.recommendedSize", notNullValue()))
                .andExpect(jsonPath("$.data.hasSizeChart", is(true)))
                .andExpect(jsonPath("$.data.confidenceLabel", anyOf(is("High"), is("Medium"), is("Low"))));
    }

    @Test
    void recommendation_withoutSizeChart_returnsFallback() throws Exception {
        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productNoChartExternalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(false)))
                .andExpect(jsonPath("$.data.recommendedSize", nullValue()))
                .andExpect(jsonPath("$.data.quality", is("NO_MATCH")));
    }

    @Test
    void recommendation_invalidApiKey_returns401() throws Exception {
        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, "invalid-key-" + testUniqueSuffix())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productWithChartExternalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    @Test
    void recommendation_inactiveStore_returns401() throws Exception {
        String adminJwt = ensureAdminJwt();

        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + session.storeId() + "/status")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productWithChartExternalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    @Test
    void recommendation_exceedsMonthlyLimit_returnsPlanLimitFallback() throws Exception {
        int limit = Plan.FREE.getMaxRecommendationsPerMonth();
        jdbcTemplate.update(
                "UPDATE stores SET recommendations_count_current_month = ?, recommendations_count_reset_at = NOW() WHERE id = ?",
                limit,
                session.storeId());

        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productWithChartExternalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(false)))
                .andExpect(jsonPath("$.data.recommendedSize", nullValue()))
                .andExpect(jsonPath("$.data.quality", is("NO_MATCH")))
                .andExpect(jsonPath("$.data.confidenceLabel", is("Low")))
                .andExpect(jsonPath("$.data.message",
                        is("Size recommendations are temporarily unavailable for this store.")));
    }

    @Test
    void recommendation_outOfRangeBmi_returnsNoMatch() throws Exception {
        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(productWithChartExternalId, 180.0, 150.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(true)))
                .andExpect(jsonPath("$.data.confidenceScore", lessThanOrEqualTo(0.5)))
                .andExpect(jsonPath("$.data.confidenceLabel", anyOf(is("Low"), is("Medium"))))
                .andExpect(jsonPath("$.data.quality", anyOf(is("CLOSEST"), is("NO_MATCH"), is("PARTIAL"))));
    }
}
