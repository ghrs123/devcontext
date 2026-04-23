package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    Optional<Brand> findBySlug(String slug);

    // Returns the store's own brands AND all FitVision-managed global brands (tenantId IS NULL).
    List<Brand> findAllByTenantIdOrTenantIdIsNull(UUID tenantId);

    // @Query required: Spring Data naming would produce (id AND tenantId) OR (tenantId IS NULL),
    // but we need id AND (tenantId = :tenantId OR tenantId IS NULL).
    @Query("SELECT b FROM Brand b WHERE b.id = :id AND (b.tenantId = :tenantId OR b.tenantId IS NULL)")
    Optional<Brand> findByIdAndTenantIdOrTenantIdIsNull(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
}
