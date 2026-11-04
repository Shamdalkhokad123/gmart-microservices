package com.gmart.repository;

import com.gmart.entity.PendingStockRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Date;
import java.util.List;

public interface PendingStockReleaseRepository extends JpaRepository<PendingStockRelease, Long> {

    List<PendingStockRelease> findTop50ByNextAttemptAtBeforeOrderByIdAsc(Date now);
}
