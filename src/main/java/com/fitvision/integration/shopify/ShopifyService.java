package com.fitvision.integration.shopify;

import com.fitvision.domain.store.Store;
import com.fitvision.domain.store.StoreRole;
import com.fitvision.infrastructure.persistence.StoreRepository;
import com.fitvision.infrastructure.security.JwtService;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

@Service
public class ShopifyService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyService.class);
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final String encryptionKeyBase64;
    private final StoreRepository storeRepository;
    private final JwtService jwtService;

    private SecretKey encryptionKey;

    public ShopifyService(
            @Value("${fitvision.shopify.encryption-key}") String encryptionKeyBase64,
            StoreRepository storeRepository,
            JwtService jwtService) {
        this.encryptionKeyBase64 = encryptionKeyBase64;
        this.storeRepository = storeRepository;
        this.jwtService = jwtService;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "fitvision.shopify.encryption-key must decode to exactly 32 bytes (AES-256)");
        }
        this.encryptionKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Links a Shopify store to a FitVision account, creating one if it doesn't exist.
     * Returns a JWT and public API key for the linked store.
     */
    @Transactional
    public ShopifyConnectResult connectStore(String shop, String accessToken, String shopName) {
        Store store = storeRepository.findByShopifyShop(shop)
                .orElseGet(() -> createStoreForShop(shop, shopName));

        store.setShopifyAccessTokenEncrypted(encryptToken(accessToken));
        if (!"ACTIVE".equals(store.getStatus())) {
            store.setStatus("ACTIVE");
        }
        store.setUpdatedAt(LocalDateTime.now());
        Store saved = storeRepository.save(store);

        String jwt = jwtService.generateToken(saved.getId(), saved.getEmail(), StoreRole.STORE.name());
        log.info("Shopify store connected: shop={} storeId={}", shop, saved.getId());
        return new ShopifyConnectResult(jwt, saved.getApiKeyPublic(), saved.getId());
    }

    public ShopifyStatusResult getStatus(String shop) {
        return storeRepository.findByShopifyShop(shop)
                .map(s -> new ShopifyStatusResult(true, s.getId(), s.getApiKeyPublic()))
                .orElse(new ShopifyStatusResult(false, null, null));
    }

    public String encryptToken(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // IV prepended to ciphertext for use during decryption
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new FitVisionException(ErrorCode.SHOPIFY_CONNECT_ERROR, "Failed to encrypt access token");
        }
    }

    public String decryptToken(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new FitVisionException(ErrorCode.SHOPIFY_CONNECT_ERROR, "Failed to decrypt access token");
        }
    }

    private Store createStoreForShop(String shop, String shopName) {
        String apiKeyPublic = generateApiKey();
        String apiKeySecret = generateDistinctApiKey(apiKeyPublic);
        String email = "shopify+" + shop;

        return Store.builder()
                .id(UUID.randomUUID())
                .name(shopName != null ? shopName : shop)
                .email(email)
                .plan("FREE")
                .status("ACTIVE")
                .apiKeyPublic(apiKeyPublic)
                .apiKeySecret(apiKeySecret)
                .platform("shopify")
                .subscriptionStatus("active")
                .role(StoreRole.STORE.name())
                .shopifyShop(shop)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private String generateApiKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateDistinctApiKey(String existing) {
        String candidate = generateApiKey();
        while (candidate.equals(existing)) {
            candidate = generateApiKey();
        }
        return candidate;
    }

    public record ShopifyConnectResult(String jwt, String apiKeyPublic, UUID storeId) {}
    public record ShopifyStatusResult(boolean connected, UUID storeId, String apiKeyPublic) {}
}
