package com.fitvision.api.dashboard.product;

import com.fitvision.domain.product.ProductService;
import com.fitvision.infrastructure.security.TenantContext;
import com.fitvision.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/v1/products")
@Tag(name = "Dashboard")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> listProducts() {
        UUID tenantId = TenantContext.get();
        List<ProductResponse> responses = productService.listProducts(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid @RequestBody ProductRequest request) {
        UUID tenantId = TenantContext.get();
        ProductResponse response = productService.createProduct(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable UUID productId) {
        UUID tenantId = TenantContext.get();
        ProductResponse response = productService.getProduct(tenantId, productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(@PathVariable UUID productId,
                                                                      @Valid @RequestBody ProductRequest request) {
        UUID tenantId = TenantContext.get();
        ProductResponse response = productService.updateProduct(tenantId, productId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable UUID productId) {
        UUID tenantId = TenantContext.get();
        productService.deleteProduct(tenantId, productId);
        return ResponseEntity.noContent().build();
    }
}
