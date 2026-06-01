package com.fitvision.integration.flow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fitvision.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProductFlowIT extends AbstractIntegrationTest {

    private StoreSession session;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = testUniqueSuffix();
        session = registerAndLogin(
                "Product Flow Store",
                "flow-products-" + suffix + "@test.com",
                "StrongPass#123");
    }

    @AfterEach
    void tearDown() {
        cleanupStoreData(session != null ? session.storeId() : null);
    }

    @Test
    void createProduct_withoutBrand_succeeds() throws Exception {
        String externalId = "ext-no-brand-" + testUniqueSuffix();

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest(externalId, "No Brand Shirt", "tops", "male", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.brandId", nullValue()));
    }

    @Test
    void createProduct_withBrand_succeeds() throws Exception {
        String brandName = "Flow Brand " + testUniqueSuffix();
        UUID brandId = createBrand(session.jwt(), brandName);
        String externalId = "ext-with-brand-" + testUniqueSuffix();

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest(externalId, "Branded Shirt", "tops", "male", brandId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.brandId", is(brandId.toString())))
                .andExpect(jsonPath("$.data.brandName", is(brandName)));
    }

    @Test
    void createProduct_exceedsFreePlanLimit_returns402() throws Exception {
        createProductViaApi(session.jwt(), "limit-1-" + testUniqueSuffix(), "Product One", null);
        createProductViaApi(session.jwt(), "limit-2-" + testUniqueSuffix(), "Product Two", null);

        mockMvc.perform(post(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest("limit-3-" + testUniqueSuffix(), "Product Three", "tops", "male", null)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("PLAN_LIMIT_REACHED")));
    }

    @Test
    void softDeleteProduct_disappearsFromList() throws Exception {
        UUID productId = createProductViaApi(session.jwt(), "delete-" + testUniqueSuffix(), "Delete Me", null);

        mockMvc.perform(delete(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PRODUCTS_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        mockMvc.perform(get(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void updateProduct_partialUpdate_onlyChangesSpecifiedFields() throws Exception {
        String externalId = "partial-" + testUniqueSuffix();
        UUID productId = createProductViaApi(session.jwt(), externalId, "Original Name", null);

        MvcResult beforeUpdate = mockMvc.perform(get(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode before = objectMapper.readTree(beforeUpdate.getResponse().getContentAsString()).path("data");
        String originalCategory = before.path("category").asText();
        String originalGender = before.path("genderTarget").asText();

        mockMvc.perform(put(PRODUCTS_URL + "/" + productId)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildProductRequest(externalId, "Updated Name Only", originalCategory, originalGender, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated Name Only")))
                .andExpect(jsonPath("$.data.category", is(originalCategory)))
                .andExpect(jsonPath("$.data.genderTarget", is(originalGender)))
                .andExpect(jsonPath("$.data.externalProductId", is(externalId)))
                .andExpect(jsonPath("$.data.brandId", nullValue()));
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
        if (brandId != null) {
            body.put("brandId", brandId.toString());
        }
        return objectMapper.writeValueAsString(body);
    }
}
