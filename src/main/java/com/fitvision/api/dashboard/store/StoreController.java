package com.fitvision.api.dashboard.store;

import com.fitvision.domain.store.Store;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.exception.StoreNotFoundException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/store")
@Tag(name = "Dashboard")
public class StoreController {

    private final StoreRepository storeRepository;

    public StoreController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<StoreProfileResponse>> getProfile() {
        Store store = loadAuthenticatedStore();
        return ResponseEntity.ok(ApiResponse.ok(toProfileResponse(store)));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<StoreProfileResponse>> updateProfile(@RequestBody UpdateStoreProfileRequest request) {
        Store store = loadAuthenticatedStore();

        if (request.getName() != null && !request.getName().isBlank()) {
            store.setName(request.getName().trim());
        }
        if (request.getPlatform() != null && !request.getPlatform().isBlank()) {
            store.setPlatform(request.getPlatform().trim().toLowerCase());
        }
        store.setUpdatedAt(LocalDateTime.now());

        Store saved = storeRepository.save(store);
        return ResponseEntity.ok(ApiResponse.ok(toProfileResponse(saved)));
    }

    @GetMapping("/api-keys")
    public ResponseEntity<ApiResponse<Map<String, String>>> getApiKeys() {
        Store store = loadAuthenticatedStore();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "apiKeyPublic", store.getApiKeyPublic(),
                "apiKeySecret", store.getApiKeySecret()
        )));
    }

    @PostMapping("/api-keys/regenerate")
    public ResponseEntity<ApiResponse<Map<String, String>>> regenerateApiKeys() {
        Store store = loadAuthenticatedStore();

        String newPublicKey = generateApiKey();
        String newSecretKey = generateDistinctApiKey(newPublicKey);

        store.setApiKeyPublic(newPublicKey);
        store.setApiKeySecret(newSecretKey);
        store.setUpdatedAt(LocalDateTime.now());
        storeRepository.save(store);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "apiKeyPublic", newPublicKey,
                "apiKeySecret", newSecretKey
        )));
    }

    private Store loadAuthenticatedStore() {
        UUID tenantId = TenantContext.get();
        return storeRepository.findById(tenantId)
                .orElseThrow(() -> new StoreNotFoundException("Store not found for tenant " + tenantId));
    }

    private StoreProfileResponse toProfileResponse(Store store) {
        return new StoreProfileResponse(
                store.getId(),
                store.getName(),
                store.getEmail(),
                store.getPlan(),
                store.getPlatform(),
                store.getApiKeyPublic(),
                store.getSubscriptionStatus()
        );
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
