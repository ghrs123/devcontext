package com.fitvision.integration.flow;

import com.fitvision.AbstractIntegrationTest;
import com.fitvision.domain.sizechart.SizeEntryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fitvision.testutil.TestDataBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SizeChartFlowIT extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate testRestTemplate;

    private StoreSession session;
    private UUID productId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = testUniqueSuffix();
        session = registerAndLogin(
                "Size Chart Flow Store",
                "flow-sizechart-" + suffix + "@test.com",
                "StrongPass#123");
        productId = createProductViaApi(session.jwt(), "sc-prod-" + suffix, "Size Chart Shirt", null);
    }

    @AfterEach
    void tearDown() {
        cleanupStoreData(session != null ? session.storeId() : null);
    }

    @Test
    void uploadCsv_validFile_createsActiveSizeChart() throws Exception {
        uploadTestCsv(session.jwt(), productId);

        mockMvc.perform(get(SIZE_CHARTS_URL + "/" + productId + "/active")
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.length()", greaterThan(0)))
                .andExpect(jsonPath("$.data[0].sizeLabel", is("S")));
    }

    @Test
    void uploadCsv_secondUpload_replacesActiveChart() throws Exception {
        byte[] csv = loadTestCsvBytes();

        mockMvc.perform(multipart(SIZE_CHARTS_URL + "/" + productId + "/upload")
                        .file(new MockMultipartFile("file", "size-chart-tops.csv", "text/csv", csv))
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", is(1)));

        mockMvc.perform(multipart(SIZE_CHARTS_URL + "/" + productId + "/upload")
                        .file(new MockMultipartFile("file", "size-chart-tops-v2.csv", "text/csv", csv))
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version", is(2)));

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM size_charts WHERE product_id = ? AND active = true",
                Integer.class,
                productId);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void uploadCsv_invalidFormat_returns400() throws Exception {
        String headerOnly = "size_label,chest_min,chest_max,waist_min,waist_max,hip_min,hip_max,height_min,height_max\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid.csv", "text/csv", headerOnly.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(SIZE_CHARTS_URL + "/" + productId + "/upload")
                        .file(file)
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("SIZE_CHART_PARSE_ERROR")));
    }

    @Test
    void uploadCsv_exceedsFileSize_returns400() {
        byte[] tooLarge = TestDataBuilder.buildLargeFileOver2Mb();
        ByteArrayResource resource = new ByteArrayResource(tooLarge) {
            @Override
            public String getFilename() {
                return "too-large.csv";
            }
        };

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add("file", resource);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(AUTHORIZATION_HEADER, testBearer(session.jwt()));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(multipartBody, headers);
        ResponseEntity<String> response = testRestTemplate.exchange(
                "http://localhost:" + port + SIZE_CHARTS_URL + "/" + productId + "/upload",
                HttpMethod.POST,
                request,
                String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("VALIDATION_ERROR");
    }

    @Test
    void manualEntry_createsActiveSizeChart() throws Exception {
        List<SizeEntryData> entries = List.of(
                new SizeEntryData("S", 85.0, 90.0, 65.0, 70.0, 88.0, 93.0, 160.0, 170.0),
                new SizeEntryData("M", 90.0, 96.0, 70.0, 76.0, 93.0, 99.0, 170.0, 180.0));

        uploadManualSizeChart(session.jwt(), productId, entries);

        mockMvc.perform(get(SIZE_CHARTS_URL + "/" + productId + "/active")
                        .header(AUTHORIZATION_HEADER, testBearer(session.jwt())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[0].sizeLabel", is("S")))
                .andExpect(jsonPath("$.data[0].chestMin", is(85.0)))
                .andExpect(jsonPath("$.data[1].sizeLabel", is("M")))
                .andExpect(jsonPath("$.data[1].chestMax", is(96.0)));
    }
}
