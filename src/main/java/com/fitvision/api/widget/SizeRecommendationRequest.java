package com.fitvision.api.widget;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body DTO for POST /api/widget/v1/size-recommendation.
 *
 * <p>All fields are provided by the buyer via the embedded widget. The store's tenant ID
 * is never accepted from the request body — it is read from {@code TenantContext} (set by
 * the API key filter).
 */
public class SizeRecommendationRequest {

    /**
     * The Shopify / WooCommerce / other platform product ID.
     * Used to look up the FitVision product record for this store.
     */
    @NotBlank(message = "externalProductId is required")
    private String externalProductId;

    /** Buyer's height in centimetres. Must be between 50 and 250. */
    @DecimalMin(value = "50", message = "heightCm must be at least 50")
    @DecimalMax(value = "250", message = "heightCm must be at most 250")
    private double heightCm;

    /** Buyer's weight in kilograms. Must be between 20 and 300. */
    @DecimalMin(value = "20", message = "weightKg must be at least 20")
    @DecimalMax(value = "300", message = "weightKg must be at most 300")
    private double weightKg;

    /**
     * Optional gender for the recommendation: "MALE", "FEMALE", or "UNISEX".
     * Any unrecognised value defaults to UNISEX — never throws.
     */
    private String gender;

    /** Optional buyer age. Must be between 10 and 120 when provided. */
    @Min(value = 10, message = "age must be at least 10")
    @Max(value = 120, message = "age must be at most 120")
    private Integer age;

    /**
     * GDPR consent flag. When {@code true}, raw body measurements (height and weight) are
     * stored in the analytics record. Defaults to {@code false}.
     */
    private boolean storeBodyData = false;

    // --- Getters and setters ---

    public String getExternalProductId() {
        return externalProductId;
    }

    public void setExternalProductId(String externalProductId) {
        this.externalProductId = externalProductId;
    }

    public double getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(double heightCm) {
        this.heightCm = heightCm;
    }

    public double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(double weightKg) {
        this.weightKg = weightKg;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public boolean isStoreBodyData() {
        return storeBodyData;
    }

    public void setStoreBodyData(boolean storeBodyData) {
        this.storeBodyData = storeBodyData;
    }
}
