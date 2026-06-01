package com.fitvision.api.dashboard;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fitvision.shared.exception.SizeChartNotFoundException;
import com.fitvision.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

@Profile("dev")
@RestController
@RequestMapping("/api/health")
@Tag(name = "Dashboard")
public class DevErrorTestController {

    @GetMapping("/error-test")
    public ResponseEntity<ApiResponse<Void>> errorTest() {
        throw new SizeChartNotFoundException("Test error - GlobalExceptionHandler is working");
    }
}
