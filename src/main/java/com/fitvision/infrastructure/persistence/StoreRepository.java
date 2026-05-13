package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.store.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByApiKeyPublic(String apiKeyPublic);

    Optional<Store> findByApiKeySecret(String apiKeySecret);

    Optional<Store> findByEmail(String email);

    boolean existsByRole(String role);

        long countByStatus(String status);

        @Query("""
                        SELECT s FROM Store s
                        WHERE (:status = 'ALL' OR s.status = :status)
                            AND (:search IS NULL OR :search = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                     OR LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%')))
                        ORDER BY s.createdAt DESC
                        """)
        Page<Store> findAdminStores(@Param("status") String status,
                                                                @Param("search") String search,
                                                                Pageable pageable);

        List<Store> findAllByOrderByCreatedAtDesc();
}
