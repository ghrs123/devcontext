package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.scraping.ScrapeJob;
import com.fitvision.domain.scraping.ScrapeJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScrapeJobRepository extends JpaRepository<ScrapeJob, UUID> {

    List<ScrapeJob> findAllByBrandIdOrderByCreatedAtDesc(UUID brandId);

    boolean existsByBrandIdAndStatus(UUID brandId, ScrapeJobStatus status);

    Page<ScrapeJob> findAllByStatusOrderByCreatedAtDesc(ScrapeJobStatus status, Pageable pageable);

    Page<ScrapeJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
