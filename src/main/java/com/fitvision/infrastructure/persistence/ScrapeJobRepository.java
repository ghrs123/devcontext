package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.scraping.ScrapeJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {

    List<ScrapeJob> findAllByBrandIdOrderByCreatedAtDesc(UUID brandId);
}
