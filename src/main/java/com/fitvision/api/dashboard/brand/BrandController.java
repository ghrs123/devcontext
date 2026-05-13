package com.fitvision.api.dashboard.brand;

import com.fitvision.domain.brand.Brand;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/brands")
@Tag(name = "Dashboard - Brands")
public class BrandController {

    private static final String SOURCE_STORE_UPLOADED = "store_uploaded";

    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;

    public BrandController(BrandRepository brandRepository, ProductRepository productRepository) {
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> listBrands() {
        UUID tenantId = requireTenantId();

        List<BrandResponse> data = brandRepository.findAllByTenantIdOrTenantIdIsNull(tenantId)
                .stream()
                .map(BrandResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        UUID tenantId = requireTenantId();
        String slug = slugify(request.name());
        if (slug.isBlank()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Brand name must contain letters or numbers");
        }

        if (brandRepository.findBySlugAndTenantId(slug, tenantId).isPresent()) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "A brand with this name already exists for your store");
        }

        if (brandRepository.existsBySlugAndTenantIdIsNullAndDeletedAtIsNull(slug)) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "This brand conflicts with a global brand slug");
        }

        Brand brand = new Brand();
        brand.setId(UUID.randomUUID());
        brand.setTenantId(tenantId);
        brand.setName(request.name().trim());
        brand.setSlug(slug);
        brand.setSource(SOURCE_STORE_UPLOADED);
        brand.setCreatedAt(LocalDateTime.now());

        Brand saved = brandRepository.save(brand);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(BrandResponse.from(saved)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteBrand(@PathVariable UUID id) {
        UUID tenantId = requireTenantId();
        Brand brand = brandRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new FitVisionException(ErrorCode.BRAND_NOT_FOUND, "Brand not found"));

        if (brand.getTenantId() == null) {
            throw new FitVisionException(ErrorCode.VALIDATION_ERROR, "Global brands cannot be deleted from dashboard");
        }

        productRepository.clearBrandAssociation(tenantId, brand.getId());

        brand.setDeletedAt(LocalDateTime.now());
        brand.setSlug(brand.getSlug() + "-deleted-" + brand.getId().toString().substring(0, 8));
        brandRepository.save(brand);

        return ResponseEntity.noContent().build();
    }

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            throw new FitVisionException(ErrorCode.UNAUTHORIZED, "Unauthorized");
        }
        return tenantId;
    }

    private String slugify(String input) {
        String cleaned = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceAll("[^a-z0-9\\s-]", "");
        cleaned = cleaned.replaceAll("\\s+", "-");
        cleaned = cleaned.replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^-", "");
        return cleaned.replaceAll("-$", "");
    }
}
