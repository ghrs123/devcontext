package com.fitvision.domain.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "subscription_status", nullable = false)
    private String subscriptionStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
