package com.fitvision.domain.product;

import com.fitvision.api.dashboard.product.ProductRequest;
import com.fitvision.api.dashboard.product.ProductResponse;
import com.fitvision.domain.billing.PlanLimitsService;
import com.fitvision.domain.brand.Brand;
import com.fitvision.domain.sizechart.SizeChartService;
import com.fitvision.infrastructure.persistence.BrandRepository;
import com.fitvision.infrastructure.persistence.ProductRepository;
import com.fitvision.infrastructure.persistence.SizeChartRepository;
import com.fitvision.shared.exception.BrandNotFoundException;
import com.fitvision.shared.exception.ErrorCode;
import com.fitvision.shared.exception.FitVisionException;
import com.fitvision.shared.exception.ProductNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final String DEFAULT_CATEGORY = "other";
    private static final String DEFAULT_GENDER_TARGET = "unisex";

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final SizeChartRepository sizeChartRepository;
    private final SizeChartService sizeChartService;
    private final PlanLimitsService planLimitsService;

    public ProductService(ProductRepository productRepository,
                          BrandRepository brandRepository,
                          SizeChartRepository sizeChartRepository,
                          SizeChartService sizeChartService,
                          PlanLimitsService planLimitsService) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.sizeChartRepository = sizeChartRepository;
        this.sizeChartService = sizeChartService;
        this.planLimitsService = planLimitsService;
    }

    public List<ProductResponse> listProducts(UUID tenantId) {
        List<Product> products = productRepository.findAllByTenantId(tenantId);
        if (products.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> brandNames = loadBrandNames(tenantId);
        List<UUID> productIds = products.stream().map(Product::getId).toList();
        Set<UUID> activeChartProductIds = sizeChartRepository.findActiveProductIdsByProductIds(productIds);

        return products.stream()
                .map(product -> toResponse(product, brandNames.get(product.getBrandId()),
                        activeChartProductIds.contains(product.getId())))
                .toList();
    }

    public ProductResponse getProduct(UUID tenantId, UUID productId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));

        String brandName = loadBrandNameForProduct(tenantId, product.getBrandId());
        boolean hasSizeChart = sizeChartRepository.findActiveByProductIdAndTenantId(product.getId(), tenantId).isPresent();
        return toResponse(product, brandName, hasSizeChart);
    }

    @Transactional
    public ProductResponse createProduct(UUID tenantId, ProductRequest request) {
        planLimitsService.checkProductLimit(tenantId);

        String externalProductId = request.getExternalProductId().trim();
        productRepository.findByExternalProductIdAndTenantId(externalProductId, tenantId)
                .ifPresent(existing -> {
                    throw new FitVisionException(ErrorCode.VALIDATION_ERROR,
                            "A product with externalProductId '" + externalProductId + "' already exists.");
                });

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setTenantId(tenantId);
        product.setBrandId(null);

        if (request.getBrandId() != null) {
            Brand brand = brandRepository
                .findByIdAndTenantIdOrTenantIdIsNull(request.getBrandId(), tenantId)
                .orElseThrow(() -> new BrandNotFoundException(
                    "Brand " + request.getBrandId() + " not found for tenant " + tenantId));
            product.setBrand(brand);
        }

        product.setExternalProductId(externalProductId);
        product.setName(request.getName().trim());
        product.setCategory(normalizeOrDefault(request.getCategory(), DEFAULT_CATEGORY));
        product.setGenderTarget(normalizeOrDefault(request.getGenderTarget(), DEFAULT_GENDER_TARGET));
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        product.setDeletedAt(null);

        Product saved = productRepository.save(product);
        String brandName = loadBrandNameForProduct(tenantId, saved.getBrandId());
        return toResponse(saved, brandName, false);
    }

    @Transactional
    public ProductResponse updateProduct(UUID tenantId, UUID productId, ProductRequest request) {
        Product existing = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));

        String externalProductId = request.getExternalProductId().trim();
        productRepository.findByExternalProductIdAndTenantId(externalProductId, tenantId)
                .ifPresent(other -> {
                    if (!other.getId().equals(productId)) {
                        throw new FitVisionException(ErrorCode.VALIDATION_ERROR,
                                "A product with externalProductId '" + externalProductId + "' already exists.");
                    }
                });

        if (request.getBrandId() != null) {
            Brand brand = brandRepository
                .findByIdAndTenantIdOrTenantIdIsNull(request.getBrandId(), tenantId)
                .orElseThrow(() -> new BrandNotFoundException(
                    "Brand " + request.getBrandId() + " not found for tenant " + tenantId));
            existing.setBrand(brand);
        } else {
            existing.setBrandId(null);
        }

        existing.setExternalProductId(externalProductId);
        existing.setName(request.getName().trim());
        existing.setCategory(normalizeOrDefault(request.getCategory(), DEFAULT_CATEGORY));
        existing.setGenderTarget(normalizeOrDefault(request.getGenderTarget(), DEFAULT_GENDER_TARGET));
        existing.setUpdatedAt(LocalDateTime.now());

        Product saved = productRepository.save(existing);
        String brandName = loadBrandNameForProduct(tenantId, saved.getBrandId());
        boolean hasSizeChart = sizeChartRepository.findActiveByProductIdAndTenantId(saved.getId(), tenantId).isPresent();
        return toResponse(saved, brandName, hasSizeChart);
    }

    @Transactional
    public void deleteProduct(UUID tenantId, UUID productId) {
        Product existing = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product " + productId + " not found for tenant " + tenantId));

        if (sizeChartRepository.findActiveByProductIdAndTenantId(productId, tenantId).isPresent()) {
            sizeChartService.deactivateActiveSizeChart(tenantId, productId);
        }

        existing.setDeletedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());
        productRepository.save(existing);
    }

    private Map<UUID, String> loadBrandNames(UUID tenantId) {
        Map<UUID, String> brandNames = new HashMap<>();
        brandRepository.findAllByTenantIdOrTenantIdIsNull(tenantId)
                .forEach(brand -> brandNames.put(brand.getId(), brand.getName()));
        return brandNames;
    }

    private String loadBrandNameForProduct(UUID tenantId, UUID brandId) {
        if (brandId == null) {
            return null;
        }
        return brandRepository.findByIdAndTenantIdOrTenantIdIsNull(brandId, tenantId)
                .map(Brand::getName)
                .orElse(null);
    }

    private ProductResponse toResponse(Product product, String brandName, boolean hasSizeChart) {
        return new ProductResponse(
                product.getId(),
                product.getExternalProductId(),
                product.getName(),
                product.getCategory(),
                product.getGenderTarget(),
                product.getBrandId(),
                brandName,
                hasSizeChart
        );
    }

    private String normalizeOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
