package com.gmart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.client.CatalogClient;
import com.gmart.client.CatalogClient.StockLine;
import com.gmart.entity.PendingStockRelease;
import com.gmart.repository.PendingStockReleaseRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Reliable stock release ("outbox"): a release is first saved in order-service's own
 * database, then sent to catalog-service. If catalog-service is down, the scheduled job
 * keeps retrying with back-off. catalog-service ignores repeats (idempotent releaseId).
 */
@Service
public class StockReleaseService {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseService.class);
    private static final TypeReference<List<StockLine>> LINES = new TypeReference<>() {};
    /** After this many failures the release still retries, but is logged as needing attention. */
    static final int ALERT_AFTER_ATTEMPTS = 10;

    private final PendingStockReleaseRepository repository;
    private final CatalogClient catalog;
    private final ObjectMapper objectMapper;

    public StockReleaseService(PendingStockReleaseRepository repository, CatalogClient catalog, ObjectMapper objectMapper) {
        this.repository = repository;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    /**
     * Records stock to give back. Call inside the transaction that cancels the order, so
     * the two are saved together; then call {@link #sendNow} after the commit.
     */
    public Long enqueue(Integer orderId, List<StockLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        PendingStockRelease release = new PendingStockRelease();
        release.setReleaseId(UUID.randomUUID().toString());
        release.setOrderId(orderId);
        release.setLinesJson(toJson(lines));
        release.setAttempts(0);
        release.setCreatedAt(new Date());
        release.setNextAttemptAt(new Date());
        return repository.save(release).getId();
    }

    /** Tries to send one release right away; on failure the scheduled job retries it. */
    public void sendNow(Long id) {
        if (id != null) {
            repository.findById(id).ifPresent(this::attempt);
        }
    }

    /** Retries releases that are due (every 30 s, on one instance at a time). */
    @Scheduled(fixedDelayString = "${gmart.stock-release.retry-ms:30000}", initialDelay = 15000)
    @SchedulerLock(name = "stock-release-retry", lockAtMostFor = "PT5M")
    public void retryDue() {
        for (PendingStockRelease release : repository.findTop50ByNextAttemptAtBeforeOrderByIdAsc(new Date())) {
            attempt(release);
        }
    }

    private void attempt(PendingStockRelease release) {
        try {
            catalog.release(release.getReleaseId(), fromJson(release.getLinesJson()));
            repository.delete(release);
            if (release.getAttempts() > 0) {
                log.info("Stock release {} (order {}) succeeded after {} retries", release.getReleaseId(), release.getOrderId(), release.getAttempts());
            }
        } catch (RuntimeException e) {
            int attempts = release.getAttempts() + 1;
            release.setAttempts(attempts);
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            release.setLastError(message.length() > 500 ? message.substring(0, 500) : message);
            // 1, 2, 4, 8 ... minutes, capped at one hour
            long delayMinutes = Math.min(60, 1L << Math.min(attempts - 1, 6));
            release.setNextAttemptAt(new Date(System.currentTimeMillis() + delayMinutes * 60_000L));
            repository.save(release);
            if (attempts >= ALERT_AFTER_ATTEMPTS) {
                log.error("Stock release {} for order {} has failed {} times and needs attention: {}",
                        release.getReleaseId(), release.getOrderId(), attempts, message);
            } else {
                log.warn("Stock release {} for order {} failed (attempt {}), retrying in {} min: {}",
                        release.getReleaseId(), release.getOrderId(), attempts, delayMinutes, message);
            }
        }
    }

    private String toJson(List<StockLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise stock lines", e);
        }
    }

    private List<StockLine> fromJson(String json) {
        try {
            return objectMapper.readValue(json, LINES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt stock release payload", e);
        }
    }
}
