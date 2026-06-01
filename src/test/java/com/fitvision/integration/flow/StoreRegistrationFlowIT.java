package com.fitvision.integration.flow;

import com.fitvision.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StoreRegistrationFlowIT extends AbstractIntegrationTest {

    private UUID storeIdToCleanup;

    @AfterEach
    void tearDown() {
        cleanupStoreData(storeIdToCleanup);
        storeIdToCleanup = null;
    }

    @Test
    void register_login_getProfile_returnsCorrectData() throws Exception {
        String suffix = testUniqueSuffix();
        String email = "flow-reg-" + suffix + "@test.com";
        String password = "StrongPass#123";
        String storeName = "Flow Test Store";

        StoreSession session = registerAndLogin(storeName, email, password);
        storeIdToCleanup = session.storeId();

        mockMvc.perform(get(STORE_PROFILE_URL)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.name", is(storeName)))
                .andExpect(jsonPath("$.data.email", is(email)))
                .andExpect(jsonPath("$.data.plan", is("FREE")));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String suffix = testUniqueSuffix();
        String email = "flow-dup-" + suffix + "@test.com";
        String password = "StrongPass#123";

        StoreSession session = registerAndLogin("First Store", email, password);
        storeIdToCleanup = session.storeId();

        mockMvc.perform(post(AUTH_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Second Store", email, password, "shopify")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("STORE_ALREADY_EXISTS")));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        String suffix = testUniqueSuffix();
        String email = "flow-bad-pass-" + suffix + "@test.com";
        String password = "StrongPass#123";

        StoreSession session = registerAndLogin("Auth Store", email, password);
        storeIdToCleanup = session.storeId();

        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest(email, "WrongPass#123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post(AUTH_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest("unknown-" + testUniqueSuffix() + "@test.com", "StrongPass#123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
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
