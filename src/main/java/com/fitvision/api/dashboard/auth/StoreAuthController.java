package com.fitvision.api.dashboard.auth;

import com.fitvision.domain.store.StoreAuthService;
import com.fitvision.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/v1/auth")
public class StoreAuthController {

    private final StoreAuthService storeAuthService;

    public StoreAuthController(StoreAuthService storeAuthService) {
        this.storeAuthService = storeAuthService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody StoreRegistrationRequest request) {
        AuthResponse response = storeAuthService.register(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody StoreLoginRequest request) {
        AuthResponse response = storeAuthService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
