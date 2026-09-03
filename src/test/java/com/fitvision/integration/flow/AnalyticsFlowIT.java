package com.fitvision.integration.flow;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AnalyticsFlowIT extends AbstractIntegrationTest {

    private StoreSession session;
    private String matchedProductExternalId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = testUniqueSuffix();
        session = registerAndLogin(
                "Analytics Flow Store",
                "flow-analytics-" + suffix + "@test.com",
                "StrongPass#123");

        UUID brandId = createBrand(session.jwt(), "Analytics Brand " + suffix);
        matchedProductExternalId = "analytics-match-" + suffix;

        UUID matchedProduct = createProductViaApi(session.jwt(), matchedProductExternalId, "Matched Product", brandId);

        // 175cm/75kg MALE → estimated chest≈98, waist≈85, hip≈96 → fully inside "M"
        uploadManualSizeChart(session.jwt(), matchedProduct, List.of(
                new SizeEntryData("M", 92.0, 102.0, 80.0, 90.0, 91.0, 101.0, null, null)));
    }

    @AfterEach
    void tearDown() {
        cleanupStoreData(session != null ? session.storeId() : null);
    }

    @Test
    void analyticsSummary_afterRecommendations_returnsCorrectCounts() throws Exception {
        for (int i = 0; i < 5; i++) {
            postRecommendation(matchedProductExternalId);
        }

        mockMvc.perform(get(ANALYTICS_URL + "/summary")
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalRecommendations", is(5)))
                .andExpect(jsonPath("$.data.qualityDistribution.EXACT", is(5)))
                .andExpect(jsonPath("$.data.qualityDistribution.NO_MATCH", is(0)))
                .andExpect(jsonPath("$.data.qualityDistribution.PARTIAL", is(0)))
                .andExpect(jsonPath("$.data.qualityDistribution.CLOSEST", is(0)));
    }

    @Test
    void analyticsList_pagination_returnsCorrectPage() throws Exception {
        for (int i = 0; i < 15; i++) {
            postRecommendation(matchedProductExternalId);
        }

        mockMvc.perform(get(ANALYTICS_URL + "/recommendations?page=0&size=10")
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.size", is(10)))
                .andExpect(jsonPath("$.data.number", is(0)))
                .andExpect(jsonPath("$.data.totalElements", is(15)))
                .andExpect(jsonPath("$.data.content.length()", is(10)));

        mockMvc.perform(get(ANALYTICS_URL + "/recommendations?page=1&size=10")
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size", is(10)))
                .andExpect(jsonPath("$.data.number", is(1)))
                .andExpect(jsonPath("$.data.totalElements", is(15)))
                .andExpect(jsonPath("$.data.content.length()", is(5)));
    }

    private void postRecommendation(String externalProductId) throws Exception {
        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(externalProductId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk());
    }
}
