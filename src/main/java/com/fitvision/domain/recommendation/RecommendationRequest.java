package com.fitvision.domain.recommendation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recommendation_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "height_cm", nullable = false, precision = 5, scale = 1)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 1)
    private BigDecimal weightKg;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "age")
    private Integer age;

    @Column(name = "recommended_size", nullable = false, length = 20)
    private String recommendedSize;

    @Column(name = "confidence_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "body_measurements_stored", nullable = false)
    private boolean bodyMeasurementsStored;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
