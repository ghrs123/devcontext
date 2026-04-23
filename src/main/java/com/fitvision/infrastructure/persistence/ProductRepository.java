package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Product> findAllByTenantId(UUID tenantId);

    Optional<Product> findByExternalProductIdAndTenantId(String externalProductId, UUID tenantId);
}
