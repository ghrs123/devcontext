package com.fitvision.domain.store;

import com.fitvision.api.dashboard.auth.AuthResponse;
import com.fitvision.api.dashboard.auth.StoreLoginRequest;
import com.fitvision.api.dashboard.auth.StoreRegistrationRequest;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StoreAuthService {

    private static final String STORE_STATUS_ACTIVE = "ACTIVE";
    private static final String DEFAULT_PLAN = "FREE";
    private static final String DEFAULT_PLATFORM = "other";
    private static final String DEFAULT_SUBSCRIPTION_STATUS = "active";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";

    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public StoreAuthService(StoreRepository storeRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService) {
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(StoreRegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (storeRepository.findByEmail(email).isPresent()) {
            throw new FitVisionException(ErrorCode.STORE_ALREADY_EXISTS,
                    "A store with this email already exists.");
        }

        String apiKeyPublic = generateApiKey();
        String apiKeySecret = generateDistinctApiKey(apiKeyPublic);

        Store store = Store.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .email(email)
                .plan(DEFAULT_PLAN)
                .status(STORE_STATUS_ACTIVE)
                .apiKeyPublic(apiKeyPublic)
                .apiKeySecret(apiKeySecret)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .platform(resolvePlatform(request.getPlatform()))
                .subscriptionStatus(DEFAULT_SUBSCRIPTION_STATUS)
                .role(StoreRole.STORE.name())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Store saved = storeRepository.save(store);
            String accessToken = jwtService.generateToken(saved.getId(), saved.getEmail(), StoreRole.STORE.name());
        return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationSeconds(), saved.getApiKeyPublic());
    }

    public AuthResponse login(StoreLoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Store store = storeRepository.findByEmail(email)
                .orElseThrow(() -> new FitVisionException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE));

        if (store.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), store.getPasswordHash())) {
            throw new FitVisionException(ErrorCode.INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
        }

        String role = store.getRole() == null || store.getRole().isBlank()
            ? StoreRole.STORE.name()
            : store.getRole();
        String accessToken = jwtService.generateToken(store.getId(), store.getEmail(), role);
        return new AuthResponse(accessToken, "Bearer", jwtService.getExpirationSeconds(), store.getApiKeyPublic());
    }

    private String resolvePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return DEFAULT_PLATFORM;
        }
        return platform.trim().toLowerCase();
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
