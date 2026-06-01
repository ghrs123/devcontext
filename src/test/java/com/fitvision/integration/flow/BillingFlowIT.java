package com.fitvision.integration.flow;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.billing.Plan;
import com.fitvision.domain.billing.StripeService;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BillingFlowIT extends AbstractIntegrationTest {

    private static final String TEST_CHECKOUT_URL = "https://checkout.stripe.com/test";
    private static final String TEST_CUSTOMER_ID = "cus_test_billing_flow";

    @MockBean
    private StripeService stripeService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${stripe.prices.starter}")
    private String starterPriceId;

    private StoreSession session;

    @BeforeEach
    void setUp() throws Exception {
        when(stripeService.createCustomer(anyString(), anyString())).thenReturn(TEST_CUSTOMER_ID);
        when(stripeService.createCheckoutSession(
                eq(TEST_CUSTOMER_ID), anyString(), anyString(), anyString()))
                .thenReturn(TEST_CHECKOUT_URL);

        String suffix = testUniqueSuffix();
        session = registerAndLogin(
                "Billing Flow Store",
                "flow-billing-" + suffix + "@test.com",
                "StrongPass#123");
    }

    @AfterEach
    void tearDown() {
        cleanupStoreData(session != null ? session.storeId() : null);
    }

    @Test
    void billingStatus_freePlan_returnsCorrectLimits() throws Exception {
        mockMvc.perform(get(BILLING_STATUS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.plan", is("FREE")))
                .andExpect(jsonPath("$.data.productsLimit", is(Plan.FREE.getMaxProducts())))
                .andExpect(jsonPath("$.data.recommendationsLimit", is(Plan.FREE.getMaxRecommendationsPerMonth())));
    }

    @Test
    void checkout_validPlan_returnsCheckoutUrl() throws Exception {
        mockMvc.perform(post(BILLING_CHECKOUT_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("plan", "STARTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.checkoutUrl", is(TEST_CHECKOUT_URL)));
    }

    @Test
    void stripeWebhook_subscriptionCreated_upgradesPlan() throws Exception {
        jdbcTemplate.update(
                "UPDATE stores SET stripe_customer_id = ? WHERE id = ?",
                TEST_CUSTOMER_ID,
                session.storeId());

        String payload = buildSubscriptionEventPayload(
                "customer.subscription.created",
                "sub_created_test",
                TEST_CUSTOMER_ID,
                starterPriceId,
                "active",
                Instant.now().getEpochSecond() + 86_400);

        mockMvc.perform(post(STRIPE_WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signStripePayload(payload))
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get(BILLING_STATUS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan", is("STARTER")))
                .andExpect(jsonPath("$.data.productsLimit", is(Plan.STARTER.getMaxProducts())));
    }

    @Test
    void stripeWebhook_subscriptionDeleted_downgradeToFree() throws Exception {
        jdbcTemplate.update(
                """
                UPDATE stores SET plan = 'STARTER', subscription_status = 'active',
                    stripe_customer_id = ?, stripe_subscription_id = 'sub_del_test',
                    stripe_price_id = ?
                WHERE id = ?
                """,
                TEST_CUSTOMER_ID,
                starterPriceId,
                session.storeId());

        String payload = buildSubscriptionEventPayload(
                "customer.subscription.deleted",
                "sub_del_test",
                TEST_CUSTOMER_ID,
                starterPriceId,
                "canceled",
                null);

        mockMvc.perform(post(STRIPE_WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", signStripePayload(payload))
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(get(BILLING_STATUS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan", is("FREE")))
                .andExpect(jsonPath("$.data.subscriptionStatus", is("inactive")));
    }

    @Test
    void stripeWebhook_invalidSignature_returns400() throws Exception {
        String payload = buildSubscriptionEventPayload(
                "customer.subscription.created",
                "sub_bad_sig",
                TEST_CUSTOMER_ID,
                starterPriceId,
                "active",
                Instant.now().getEpochSecond());

        mockMvc.perform(post(STRIPE_WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(STRIPE_WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=0,v1=invalid")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void planLimitReset_newMonth_resetsCounter() throws Exception {
        UUID brandId = createBrand(session.jwt(), "Billing Reset Brand");
        String externalId = "billing-reset-" + testUniqueSuffix();
        UUID productId = createProductViaApi(session.jwt(), externalId, "Reset Product", brandId);
        uploadManualSizeChart(session.jwt(), productId, List.of(
                new SizeEntryData("M", 115.0, 125.0, null, null, null, null, null, null)));

        LocalDateTime priorMonthReset = LocalDateTime.of(2020, 1, 15, 0, 0);
        jdbcTemplate.update(
                """
                UPDATE stores SET recommendations_count_current_month = 100,
                    recommendations_count_reset_at = ?
                WHERE id = ?
                """,
                priorMonthReset,
                session.storeId());

        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT recommendations_count_current_month FROM stores WHERE id = ?",
                Integer.class,
                session.storeId());
        org.junit.jupiter.api.Assertions.assertEquals(100, countBefore);

        mockMvc.perform(post(WIDGET_RECOMMENDATION_URL)
                        .header(API_KEY_HEADER, session.apiKeyPublic())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildWidgetRecommendationBody(externalId, 175.0, 75.0, "MALE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.hasSizeChart", is(true)));

        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT recommendations_count_current_month FROM stores WHERE id = ?",
                Integer.class,
                session.storeId());
        org.junit.jupiter.api.Assertions.assertEquals(1, countAfter);

        mockMvc.perform(get(BILLING_STATUS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendationsUsed", is(1)));
    }

    private String signStripePayload(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }

    private String buildSubscriptionEventPayload(String type,
                                                 String subscriptionId,
                                                 String customerId,
                                                 String priceId,
                                                 String status,
                                                 Long currentPeriodEnd) throws Exception {
        long periodStart = Instant.now().getEpochSecond();
        long periodEnd = currentPeriodEnd != null ? currentPeriodEnd : periodStart + 2_592_000;

        Map<String, Object> price = new java.util.LinkedHashMap<>();
        price.put("id", priceId);
        price.put("object", "price");
        price.put("active", true);
        price.put("currency", "usd");
        price.put("product", "prod_test");

        Map<String, Object> item = Map.of(
                "id", "si_test",
                "object", "subscription_item",
                "price", price);

        Map<String, Object> items = new java.util.LinkedHashMap<>();
        items.put("object", "list");
        items.put("data", List.of(item));
        items.put("has_more", false);
        items.put("total_count", 1);

        Map<String, Object> subscription = new java.util.LinkedHashMap<>();
        subscription.put("id", subscriptionId);
        subscription.put("object", "subscription");
        subscription.put("customer", customerId);
        subscription.put("status", status);
        subscription.put("currency", "usd");
        subscription.put("current_period_start", periodStart);
        subscription.put("current_period_end", periodEnd);
        subscription.put("items", items);

        Map<String, Object> event = new java.util.LinkedHashMap<>();
        event.put("id", "evt_" + testUniqueSuffix());
        event.put("object", "event");
        event.put("api_version", "2023-10-16");
        event.put("created", periodStart);
        event.put("livemode", false);
        event.put("type", type);
        event.put("data", Map.of("object", subscription));

        return objectMapper.writeValueAsString(event);
    }
}
