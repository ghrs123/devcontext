package com.fitvision.api.dashboard.brand;

import com.fitvision.domain.brand.Brand;

import java.util.UUID;

public record BrandResponse(
        UUID id,
        String name,
        String slug,
        String source,
        boolean isGlobal
) {

    public static BrandResponse from(Brand brand) {
        return new BrandResponse(
                brand.getId(),
                brand.getName(),
                brand.getSlug(),
                brand.getSource(),
                brand.getTenantId() == null
        );
    }
}
