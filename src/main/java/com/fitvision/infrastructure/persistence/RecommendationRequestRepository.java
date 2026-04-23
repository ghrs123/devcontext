package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.recommendation.RecommendationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface RecommendationRequestRepository extends JpaRepository<RecommendationRequest, UUID> {

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after);

    Page<RecommendationRequest> findAllByTenantId(UUID tenantId, Pageable pageable);
}
