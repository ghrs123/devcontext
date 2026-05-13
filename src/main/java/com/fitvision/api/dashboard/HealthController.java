package com.fitvision.api.dashboard;

import com.fitvision.shared.exception.SizeChartNotFoundException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Dashboard")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("status", "UP")));
    }

    @GetMapping("/error-test")
    public ResponseEntity<ApiResponse<Void>> errorTest() {
        throw new SizeChartNotFoundException("Test error — GlobalExceptionHandler is working");
    }
}
