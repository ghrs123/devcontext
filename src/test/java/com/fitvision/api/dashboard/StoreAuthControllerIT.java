package com.fitvision.api.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitvision.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StoreAuthControllerIT extends AbstractIntegrationTest {

    private static final String BASE_URL = "/api/dashboard/v1/auth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM stores");
    }

    @Test
    void scenario1_register_returns200WithJwtAndApiKeyPublic() throws Exception {
        String email = "register-" + uniqueSuffix() + "@test.com";

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Auth Store", email, "StrongPass#123", "shopify")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.apiKeyPublic", notNullValue()));
    }

    @Test
    void scenario2_registerDuplicateEmail_returns409StoreAlreadyExists() throws Exception {
        String email = "duplicate-" + uniqueSuffix() + "@test.com";

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Auth Store", email, "StrongPass#123", "shopify")))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Auth Store 2", email, "StrongPass#123", "shopify")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("STORE_ALREADY_EXISTS")));
    }

    @Test
    void scenario3_loginValidCredentials_returns200WithJwt() throws Exception {
        String email = "login-ok-" + uniqueSuffix() + "@test.com";
        String password = "StrongPass#123";

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Auth Store", email, password, "shopify")))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()));
    }

    @Test
    void scenario4_loginWrongPassword_returns401InvalidCredentials() throws Exception {
        String email = "login-bad-pass-" + uniqueSuffix() + "@test.com";

        mockMvc.perform(post(BASE_URL + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRegisterRequest("Auth Store", email, "StrongPass#123", "shopify")))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest(email, "WrongPass#123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void scenario5_loginUnknownEmail_returns401InvalidCredentials() throws Exception {
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildLoginRequest("unknown-" + uniqueSuffix() + "@test.com", "StrongPass#123")))
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

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
