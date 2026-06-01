package com.fitvision.domain.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Store {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "plan", nullable = false)
    private String plan;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "api_key_public", nullable = false, unique = true)
    private String apiKeyPublic;

    @Column(name = "api_key_secret", nullable = false)
    private String apiKeySecret;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus;

    @Column(name = "role", nullable = false)
    private String role;

    @Column(name = "shopify_shop", unique = true)
    private String shopifyShop;

    @Column(name = "shopify_access_token_encrypted", columnDefinition = "TEXT")
    private String shopifyAccessTokenEncrypted;

    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_price_id")
    private String stripePriceId;

    @Column(name = "subscription_current_period_end")
    private java.time.LocalDateTime subscriptionCurrentPeriodEnd;

    @Column(name = "recommendations_count_current_month")
    private Integer recommendationsCountCurrentMonth;

    @Column(name = "recommendations_count_reset_at")
    private java.time.LocalDateTime recommendationsCountResetAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (role == null || role.isBlank()) {
            role = StoreRole.STORE.name();
        }
        if (recommendationsCountCurrentMonth == null) {
            recommendationsCountCurrentMonth = 0;
        }
    }
}
