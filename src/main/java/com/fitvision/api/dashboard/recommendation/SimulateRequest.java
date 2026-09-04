package com.fitvision.api.dashboard.recommendation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Store-owner simulator input. Product is identified by its FitVision UUID or external id. */
public class SimulateRequest {

    private String productId;

    private String externalProductId;

    @DecimalMin(value = "50", message = "heightCm must be at least 50")
    @DecimalMax(value = "250", message = "heightCm must be at most 250")
    private double heightCm;

    @DecimalMin(value = "20", message = "weightKg must be at least 20")
    @DecimalMax(value = "300", message = "weightKg must be at most 300")
    private double weightKg;

    /** "MALE", "FEMALE", "UNISEX" — anything else defaults to UNISEX. */
    private String gender;

    @Min(value = 10, message = "age must be at least 10")
    @Max(value = 120, message = "age must be at most 120")
    private Integer age;

    @NotBlank(message = "productId or externalProductId is required")
    public String productRef() {
        return productId != null && !productId.isBlank() ? productId : externalProductId;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getExternalProductId() { return externalProductId; }
    public void setExternalProductId(String externalProductId) { this.externalProductId = externalProductId; }
    public double getHeightCm() { return heightCm; }
    public void setHeightCm(double heightCm) { this.heightCm = heightCm; }
    public double getWeightKg() { return weightKg; }
    public void setWeightKg(double weightKg) { this.weightKg = weightKg; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
}
