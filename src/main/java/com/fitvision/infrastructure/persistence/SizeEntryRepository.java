package com.fitvision.infrastructure.persistence;

import com.fitvision.domain.sizechart.SizeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SizeEntryRepository extends JpaRepository<SizeEntry, UUID> {

    List<SizeEntry> findAllBySizeChartId(UUID sizeChartId);
}
