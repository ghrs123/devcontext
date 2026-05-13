package com.fitvision.api.dashboard;

import com.fitvision.domain.sizechart.ParseResult;
import com.fitvision.domain.sizechart.SizeChartFileParser;
import com.fitvision.domain.sizechart.SizeChartService;
import com.fitvision.domain.sizechart.SizeChartUploadResult;
import com.fitvision.domain.sizechart.SizeEntryData;
import com.fitvision.infrastructure.parsing.SizeChartParserFactory;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/size-charts")
@Validated
@Tag(name = "Dashboard")
public class SizeChartController {

    private static final Logger log = LoggerFactory.getLogger(SizeChartController.class);

    private final SizeChartService sizeChartService;
    private final SizeChartParserFactory parserFactory;

    public SizeChartController(SizeChartService sizeChartService,
                               SizeChartParserFactory parserFactory) {
        this.sizeChartService = sizeChartService;
        this.parserFactory = parserFactory;
    }

    @PostMapping(value = "/{productId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<SizeChartUploadResult>> uploadFile(
            @PathVariable UUID productId,
            @RequestParam("file") MultipartFile file) throws IOException {
        UUID tenantId = TenantContext.get();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        SizeChartFileParser parser = parserFactory.getParser(contentType, filename);
        ParseResult result;
        try (var inputStream = file.getInputStream()) {
            result = parser.parse(inputStream);
        }
        String source = filename.toLowerCase().endsWith(".csv") ? "csv" : "xlsx";
        SizeChartUploadResult uploadResult = sizeChartService.uploadFromFile(tenantId, productId, result, source);
        return ResponseEntity.ok(ApiResponse.ok(uploadResult));
    }

    @PostMapping(value = "/{productId}/manual", consumes = "application/json")
    public ResponseEntity<ApiResponse<SizeChartUploadResult>> uploadManual(
            @PathVariable UUID productId,
            @RequestBody @NotEmpty(message = "entries must not be empty") List<SizeEntryData> entries) {
        UUID tenantId = TenantContext.get();
        SizeChartUploadResult result = sizeChartService.uploadManual(tenantId, productId, entries);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @GetMapping("/{productId}/active")
    public ResponseEntity<ApiResponse<List<SizeEntryData>>> getActive(@PathVariable UUID productId) {
        UUID tenantId = TenantContext.get();
        sizeChartService.getActiveSizeChartForTenant(tenantId, productId);
        List<SizeEntryData> entries = sizeChartService.getActiveSizeChartEntries(tenantId, productId);
        return ResponseEntity.ok(ApiResponse.ok(entries));
    }

    @DeleteMapping("/{productId}/active")
    public ResponseEntity<Void> deactivateActive(@PathVariable UUID productId) {
        UUID tenantId = TenantContext.get();
        sizeChartService.deactivateActiveSizeChart(tenantId, productId);
        return ResponseEntity.noContent().build();
    }
}
