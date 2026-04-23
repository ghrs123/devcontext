package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.sizechart.SizeChart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
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
}
