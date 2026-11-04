package com.gmart.repository;

import com.gmart.entity.ProcessedStockRelease;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedStockReleaseRepository extends JpaRepository<ProcessedStockRelease, String> {
}
