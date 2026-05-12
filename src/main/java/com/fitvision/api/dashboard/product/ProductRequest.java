package com.fitvision.api.dashboard.product;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class ProductRequest {

    @NotBlank(message = "externalProductId is required")
    private String externalProductId;

    @NotBlank(message = "name is required")
    private String name;

    private String category;
    private String genderTarget;
    private UUID brandId;

    public String getExternalProductId() {
        return externalProductId;
    }

    public void setExternalProductId(String externalProductId) {
        this.externalProductId = externalProductId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getGenderTarget() {
        return genderTarget;
    }

    public void setGenderTarget(String genderTarget) {
        this.genderTarget = genderTarget;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
    }
}
