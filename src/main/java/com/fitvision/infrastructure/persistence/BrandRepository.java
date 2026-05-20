package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.brand.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    @Query("SELECT b FROM Brand b WHERE b.slug = :slug AND b.deletedAt IS NULL")
    Optional<Brand> findBySlug(@Param("slug") String slug);

    @Query("SELECT b FROM Brand b WHERE b.slug = :slug AND b.tenantId = :tenantId AND b.deletedAt IS NULL")
    Optional<Brand> findBySlugAndTenantId(@Param("slug") String slug, @Param("tenantId") UUID tenantId);

    boolean existsBySlugAndTenantIdIsNullAndDeletedAtIsNull(String slug);

    // Returns the store's own brands AND all FitVision-managed global brands (tenantId IS NULL).
    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL AND (b.tenantId = :tenantId OR b.tenantId IS NULL) ORDER BY b.name ASC")
    List<Brand> findAllByTenantIdOrTenantIdIsNull(@Param("tenantId") UUID tenantId);

    // @Query required: Spring Data naming would produce (id AND tenantId) OR (tenantId IS NULL),
    // but we need id AND (tenantId = :tenantId OR tenantId IS NULL).
    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL AND b.id = :id AND (b.tenantId = :tenantId OR b.tenantId IS NULL)")
    Optional<Brand> findByIdAndTenantIdOrTenantIdIsNull(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL AND b.id = :id AND b.tenantId = :tenantId")
    Optional<Brand> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL ORDER BY b.createdAt DESC")
    List<Brand> findAllActive();

    @Query("SELECT b FROM Brand b WHERE b.deletedAt IS NULL AND b.id = :id AND b.tenantId IS NULL")
    Optional<Brand> findGlobalById(@Param("id") UUID id);

        @Query("""
                        SELECT b FROM Brand b
                        WHERE b.deletedAt IS NULL
                            AND b.tenantId IS NULL
                            AND (b.lastScrapedAt IS NULL OR b.lastScrapedAt < :cutoff)
                        ORDER BY b.lastScrapedAt ASC NULLS FIRST, b.createdAt ASC
                        """)
        List<Brand> findGlobalBrandsNeedingScrape(@Param("cutoff") java.time.LocalDateTime cutoff);
}
