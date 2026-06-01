package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.sizechart.SizeChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SizeChartRepository extends JpaRepository<SizeChart, UUID> {

    // @Query required for two reasons:
    // 1. "findActive" is not a Spring Data keyword prefix.
    // 2. SizeChart has no tenantId field — tenant isolation is enforced by joining through Product.
    @Query("""
            SELECT sc FROM SizeChart sc, Product p
            WHERE sc.productId = p.id
              AND sc.productId = :productId
              AND p.tenantId = :tenantId
              AND sc.active = true
            """)
    Optional<SizeChart> findActiveByProductIdAndTenantId(
            @Param("productId") UUID productId,
            @Param("tenantId") UUID tenantId);

    /**
     * Bulk-deactivates all size chart versions for a product.
     * Used before creating a new version to ensure only one active version exists at a time.
     */
    @Modifying
    @Query("UPDATE SizeChart sc SET sc.active = false WHERE sc.productId = :productId")
    void deactivateAllByProductId(@Param("productId") UUID productId);

        @Modifying
        @Query("UPDATE SizeChart sc SET sc.scrapeSourceUrl = :scrapeSourceUrl WHERE sc.id = :sizeChartId")
        void updateScrapeSourceUrl(@Param("sizeChartId") UUID sizeChartId,
                                                           @Param("scrapeSourceUrl") String scrapeSourceUrl);

    /**
     * Returns all size chart versions for a product, ordered from newest to oldest.
     * Used to determine the next version number.
     */
    @Query("SELECT sc FROM SizeChart sc WHERE sc.productId = :productId ORDER BY sc.version DESC")
    List<SizeChart> findAllByProductIdOrderByVersionDesc(@Param("productId") UUID productId);

        /**
         * Returns product IDs that currently have an active size chart.
         * Used by dashboard product listing to compute hasSizeChart without N+1 queries.
         */
        @Query("SELECT sc.productId FROM SizeChart sc WHERE sc.active = true AND sc.productId IN :productIds")
        Set<UUID> findActiveProductIdsByProductIds(@Param("productIds") List<UUID> productIds);
}

