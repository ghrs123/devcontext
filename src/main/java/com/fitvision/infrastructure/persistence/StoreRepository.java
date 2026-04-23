package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.store.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Optional<Store> findByApiKeyPublic(String apiKeyPublic);

    Optional<Store> findByEmail(String email);
}
