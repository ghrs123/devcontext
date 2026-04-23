package com.fitvision.engine.recommendation;

import com.fitvision.domain.recommendation.Gender;

import java.util.UUID;

/**
 * Input DTO for a recommendation request.
 * This is the engine-layer input — not the JPA persistence entity.
 */
public final class RecommendationInput {

    private final UUID tenantId;
    private final UUID productId;
    private final double heightCm;
    private final double weightKg;
    /** Nullable — engine defaults to UNISEX if absent. */
    private final Gender gender;
    /** Nullable — BodyProfileCalculator defaults to 30 if absent. */
    private final Integer age;
    /** GDPR consent flag: if false, raw measurements are not stored. */
    private final boolean storeBodyData;

    private RecommendationInput(Builder builder) {
        this.tenantId = builder.tenantId;
        this.productId = builder.productId;
        this.heightCm = builder.heightCm;
        this.weightKg = builder.weightKg;
        this.gender = builder.gender;
        this.age = builder.age;
        this.storeBodyData = builder.storeBodyData;
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getProductId() { return productId; }
    public double getHeightCm() { return heightCm; }
    public double getWeightKg() { return weightKg; }
    public Gender getGender() { return gender; }
    public Integer getAge() { return age; }
    public boolean isStoreBodyData() { return storeBodyData; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID tenantId;
        private UUID productId;
        private double heightCm;
        private double weightKg;
        private Gender gender;
        private Integer age;
        private boolean storeBodyData;

        public Builder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public Builder productId(UUID productId) { this.productId = productId; return this; }
        public Builder heightCm(double heightCm) { this.heightCm = heightCm; return this; }
        public Builder weightKg(double weightKg) { this.weightKg = weightKg; return this; }
        public Builder gender(Gender gender) { this.gender = gender; return this; }
        public Builder age(Integer age) { this.age = age; return this; }
        public Builder storeBodyData(boolean storeBodyData) { this.storeBodyData = storeBodyData; return this; }

        public RecommendationInput build() { return new RecommendationInput(this); }
    }
}
