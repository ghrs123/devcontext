package com.fitvision.integration.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitvision.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ShopifyWebhookIT extends AbstractIntegrationTest {

    @Value("${fitvision.shopify.shared-secret}")
    private String shopifySharedSecret;

    private UUID shopifyStoreId;

    @AfterEach
    void tearDown() {
        cleanupShopifyStore(shopifyStoreId);
        shopifyStoreId = null;
    }

    @Test
    void shopifyConnect_newStore_createsAccountAndReturnsJwt() throws Exception {
        String shop = "new-" + testUniqueSuffix() + ".myshopify.com";
        MvcResult result = connectShop(shop, "shpat_new_token", "New Shopify Store");

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        shopifyStoreId = UUID.fromString(data.path("storeId").asText());

        mockMvc.perform(get(SHOPIFY_STATUS_URL)
                        .param("shop", shop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected", is(true)))
                .andExpect(jsonPath("$.data.storeId", is(shopifyStoreId.toString())));

        String dbShop = jdbcTemplate.queryForObject(
                "SELECT shopify_shop FROM stores WHERE id = ?",
                String.class,
                shopifyStoreId);
        org.junit.jupiter.api.Assertions.assertEquals(shop, dbShop);
    }

    @Test
    void shopifyConnect_existingStore_updatesTokenAndReturns200() throws Exception {
        String shop = "existing-" + testUniqueSuffix() + ".myshopify.com";
        MvcResult first = connectShop(shop, "shpat_first", "Existing Store");
        UUID firstStoreId = UUID.fromString(
                objectMapper.readTree(first.getResponse().getContentAsString())
                        .path("data").path("storeId").asText());
        shopifyStoreId = firstStoreId;

        MvcResult second = connectShop(shop, "shpat_updated", "Existing Store Updated");
        UUID secondStoreId = UUID.fromString(
                objectMapper.readTree(second.getResponse().getContentAsString())
                        .path("data").path("storeId").asText());

        org.junit.jupiter.api.Assertions.assertEquals(firstStoreId, secondStoreId);

        String encrypted = jdbcTemplate.queryForObject(
                "SELECT shopify_access_token_encrypted FROM stores WHERE id = ?",
                String.class,
                firstStoreId);
        org.junit.jupiter.api.Assertions.assertNotNull(encrypted);
        org.junit.jupiter.api.Assertions.assertFalse(encrypted.isBlank());
    }

    @Test
    void shopifyConnect_wrongSecret_returns401() throws Exception {
        mockMvc.perform(post(SHOPIFY_CONNECT_URL)
                        .header(SHOPIFY_SECRET_HEADER, "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shop", "wrong-" + testUniqueSuffix() + ".myshopify.com",
                                "accessToken", "shpat_wrong",
                                "shopName", "Wrong Secret Store"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void shopifyStatus_connectedStore_returnsConnectedTrue() throws Exception {
        String shop = "status-" + testUniqueSuffix() + ".myshopify.com";
        MvcResult connect = connectShop(shop, "shpat_status", "Status Store");
        JsonNode data = objectMapper.readTree(connect.getResponse().getContentAsString()).path("data");
        shopifyStoreId = UUID.fromString(data.path("storeId").asText());
        String apiKey = data.path("apiKeyPublic").asText();

        mockMvc.perform(get(SHOPIFY_STATUS_URL).param("shop", shop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected", is(true)))
                .andExpect(jsonPath("$.data.apiKeyPublic", is(apiKey)));
    }

    @Test
    void shopifyStatus_unknownStore_returnsConnectedFalse() throws Exception {
        mockMvc.perform(get(SHOPIFY_STATUS_URL)
                        .param("shop", "unknown-" + testUniqueSuffix() + ".myshopify.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connected", is(false)));
    }

    @Test
    void shopifyConnect_inactiveStore_reactivatesOnReconnect() throws Exception {
        String shop = "reactivate-" + testUniqueSuffix() + ".myshopify.com";
        MvcResult connect = connectShop(shop, "shpat_react", "Reactivate Shopify");
        shopifyStoreId = UUID.fromString(
                objectMapper.readTree(connect.getResponse().getContentAsString())
                        .path("data").path("storeId").asText());

        String adminJwt = ensureAdminJwt();
        mockMvc.perform(patch(ADMIN_STORES_URL + "/" + shopifyStoreId + "/status")
                        .header(AUTHORIZATION_HEADER, testBearer(adminJwt))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "INACTIVE"))))
                .andExpect(status().isOk());

        connectShop(shop, "shpat_react_new", "Reactivate Shopify");

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM stores WHERE id = ?",
                String.class,
                shopifyStoreId);
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", status);
    }

    private MvcResult connectShop(String shop, String accessToken, String shopName) throws Exception {
        return mockMvc.perform(post(SHOPIFY_CONNECT_URL)
                        .header(SHOPIFY_SECRET_HEADER, shopifySharedSecret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "shop", shop,
                                "accessToken", accessToken,
                                "shopName", shopName))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.jwt", notNullValue()))
                .andExpect(jsonPath("$.data.apiKeyPublic", notNullValue()))
                .andExpect(jsonPath("$.data.storeId", notNullValue()))
                .andReturn();
    }
}
