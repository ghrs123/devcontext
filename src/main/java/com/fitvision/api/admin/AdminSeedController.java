package com.fitvision.api.admin;

import com.fitvision.api.dashboard.auth.AuthResponse;
import com.fitvision.domain.store.Store;
import com.fitvision.domain.store.StoreRole;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin")
public class AdminSeedController {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String PLAN_ADMIN = "ADMIN";
    private static final String PLATFORM_ADMIN = "admin";
    private static final String SUBSCRIPTION_ACTIVE = "active";

    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${fitvision.admin.seed.enabled:true}")
    private boolean seedEnabled;

    @Value("${fitvision.admin.seed.token:}")
    private String seedToken;

    public AdminSeedController(StoreRepository storeRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<AuthResponse>> seedAdmin(
            @Valid @RequestBody AdminSeedRequest request,
            @RequestHeader(value = "X-FitVision-Seed-Token", required = false) String requestSeedToken) {
        if (!seedEnabled) {
            throw new FitVisionException(ErrorCode.UNAUTHORIZED, "Admin seed endpoint is disabled.");
        }
        if (StringUtils.hasText(seedToken) && !seedToken.equals(requestSeedToken)) {
            throw new FitVisionException(ErrorCode.UNAUTHORIZED, "Invalid or missing admin seed token.");
        }

        if (storeRepository.existsByRole(StoreRole.ADMIN.name())) {
            throw new FitVisionException(ErrorCode.ADMIN_ALREADY_EXISTS,
                    "An admin account already exists. Seed endpoint is disabled.");
        }

        String email = request.getEmail().trim().toLowerCase();
        if (storeRepository.findByEmail(email).isPresent()) {
            throw new FitVisionException(ErrorCode.STORE_ALREADY_EXISTS,
                    "A store with this email already exists.");
        }

        String apiKeyPublic = generateApiKey();
        String apiKeySecret = generateDistinctApiKey(apiKeyPublic);

        Store admin = Store.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .email(email)
                .plan(PLAN_ADMIN)
                .status(STATUS_ACTIVE)
                .apiKeyPublic(apiKeyPublic)
                .apiKeySecret(apiKeySecret)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .platform(PLATFORM_ADMIN)
                .subscriptionStatus(SUBSCRIPTION_ACTIVE)
                .role(StoreRole.ADMIN.name())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Store saved = storeRepository.save(admin);
        String token = jwtService.generateToken(saved.getId(), saved.getEmail(), StoreRole.ADMIN.name());
        AuthResponse response = new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(), saved.getApiKeyPublic());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private String generateApiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateDistinctApiKey(String existingKey) {
        String candidate = generateApiKey();
        while (candidate.equals(existingKey)) {
            candidate = generateApiKey();
        }
        return candidate;
    }
}
