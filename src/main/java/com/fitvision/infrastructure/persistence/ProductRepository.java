package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<Product> findAllByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.externalProductId = :externalProductId AND p.tenantId = :tenantId AND p.deletedAt IS NULL")
    Optional<Product> findByExternalProductIdAndTenantId(@Param("externalProductId") String externalProductId,
                                                         @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("UPDATE Product p SET p.brand = NULL WHERE p.tenantId = :tenantId AND p.brand.id = :brandId AND p.deletedAt IS NULL")
    int clearBrandAssociation(@Param("tenantId") UUID tenantId, @Param("brandId") UUID brandId);

    long countByTenantIdAndDeletedAtIsNull(UUID tenantId);

    @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.brandId = :brandId AND p.deletedAt IS NULL")
    List<Product> findAllByTenantIdAndBrandId(@Param("tenantId") UUID tenantId, @Param("brandId") UUID brandId);

    @Query("SELECT p FROM Product p WHERE p.externalProductId = :externalProductId AND p.tenantId = :tenantId")
    Optional<Product> findAnyByExternalProductIdAndTenantId(@Param("externalProductId") String externalProductId,
                                                            @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("UPDATE Product p SET p.brandId = NULL, p.updatedAt = :updatedAt WHERE p.brandId = :brandId AND p.deletedAt IS NULL")
    int clearBrandAssociationGlobal(@Param("brandId") UUID brandId, @Param("updatedAt") LocalDateTime updatedAt);
}
