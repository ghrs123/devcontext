package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.recommendation.RecommendationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, UUID> {

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after);

    Page<RecommendationRequest> findAllByTenantId(UUID tenantId, Pageable pageable);

    @Query("""
        SELECT AVG(r.confidenceScore)
        FROM RecommendationRequest r
        WHERE r.tenantId = :tenantId
        """)
    Double findAverageConfidenceByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT COUNT(r)
        FROM RecommendationRequest r
        WHERE r.tenantId = :tenantId
          AND (
            (:quality = 'NO_MATCH' AND (r.recommendedSize = 'NO_MATCH' OR r.confidenceScore = 0))
         OR (:quality = 'EXACT' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 1.0)
         OR (:quality = 'PARTIAL' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 0.5 AND r.confidenceScore < 1.0)
         OR (:quality = 'CLOSEST' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore > 0 AND r.confidenceScore < 0.5)
          )
        """)
    long countByTenantIdAndQuality(@Param("tenantId") UUID tenantId, @Param("quality") String quality);

    @Query("""
        SELECT r.productId,
           COALESCE(p.name, 'Unknown Product'),
           COUNT(r),
           AVG(r.confidenceScore)
        FROM RecommendationRequest r
        LEFT JOIN com.fitvision.domain.product.Product p ON p.id = r.productId
        WHERE r.tenantId = :tenantId
        GROUP BY r.productId, p.name
        ORDER BY COUNT(r) DESC
        """)
    List<Object[]> findTopProductsByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    long countByCreatedAtAfter(LocalDateTime after);

    @Query("SELECT AVG(r.confidenceScore) FROM RecommendationRequest r")
    Double findAverageConfidenceGlobal();

    @Query("""
        SELECT COUNT(r)
        FROM RecommendationRequest r
        WHERE (
            (:quality = 'NO_MATCH' AND (r.recommendedSize = 'NO_MATCH' OR r.confidenceScore = 0))
         OR (:quality = 'EXACT' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 1.0)
         OR (:quality = 'PARTIAL' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 0.5 AND r.confidenceScore < 1.0)
         OR (:quality = 'CLOSEST' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore > 0 AND r.confidenceScore < 0.5)
        )
        """)
    long countByQualityGlobal(@Param("quality") String quality);

    @Query("""
        SELECT b.id,
               COALESCE(b.name, 'Unknown Brand'),
               COUNT(r),
               AVG(r.confidenceScore)
        FROM RecommendationRequest r
        LEFT JOIN com.fitvision.domain.product.Product p ON p.id = r.productId
        LEFT JOIN com.fitvision.domain.brand.Brand b ON b.id = p.brandId
        GROUP BY b.id, b.name
        ORDER BY COUNT(r) DESC
        """)
    List<Object[]> findTopBrands(Pageable pageable);

    @Query("SELECT MAX(r.createdAt) FROM RecommendationRequest r WHERE r.tenantId = :tenantId")
    LocalDateTime findLastRecommendationAtByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
        SELECT r
        FROM RecommendationRequest r
        WHERE (:tenantId IS NULL OR r.tenantId = :tenantId)
          AND (:productId IS NULL OR r.productId = :productId)
          AND (
              :quality IS NULL OR :quality = ''
           OR (:quality = 'NO_MATCH' AND (r.recommendedSize = 'NO_MATCH' OR r.confidenceScore = 0))
           OR (:quality = 'EXACT' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 1.0)
           OR (:quality = 'PARTIAL' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore >= 0.5 AND r.confidenceScore < 1.0)
           OR (:quality = 'CLOSEST' AND r.recommendedSize <> 'NO_MATCH' AND r.confidenceScore > 0 AND r.confidenceScore < 0.5)
          )
        ORDER BY r.createdAt DESC
        """)
    Page<RecommendationRequest> findAdminRecommendations(@Param("tenantId") UUID tenantId,
                                                         @Param("productId") UUID productId,
                                                         @Param("quality") String quality,
                                                         Pageable pageable);
}
