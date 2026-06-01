package com.fitvision.api.dashboard.product;

import java.util.UUID;

public class ProductResponse {

    private UUID id;
    private String externalProductId;
    private String name;
    private String category;
    private String genderTarget;
    private UUID brandId;
    private String brandName;
    private boolean hasSizeChart;

    public ProductResponse(UUID id,
                           String externalProductId,
                           String name,
                           String category,
                           String genderTarget,
                           UUID brandId,
                           String brandName,
                           boolean hasSizeChart) {
        this.id = id;
        this.externalProductId = externalProductId;
        this.name = name;
        this.category = category;
        this.genderTarget = genderTarget;
        this.brandId = brandId;
        this.brandName = brandName;
        this.hasSizeChart = hasSizeChart;
    }

    public UUID getId() {
        return id;
    }

    public String getExternalProductId() {
        return externalProductId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getGenderTarget() {
        return genderTarget;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public boolean isHasSizeChart() {
        return hasSizeChart;
    }
}
