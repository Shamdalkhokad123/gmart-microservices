package com.gmart.entity;

import jakarta.persistence.*;

import java.util.Date;

/**
 * Stock to give back to catalog-service. Saved in the same transaction as the
 * cancellation, so it can't be lost; deleted once catalog-service confirms.
 */
@Entity
@Table(name = "pending_stock_releases")
public class PendingStockRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Idempotency key sent to catalog-service. */
    @Column(nullable = false, length = 64, unique = true)
    private String releaseId;

    private Integer orderId;

    @Column(nullable = false, length = 4000)
    private String linesJson;

    @Column(nullable = false)
    private int attempts;

    @Column(length = 500)
    private String lastError;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date nextAttemptAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt;

    public Long getId() { return id; }
    public String getReleaseId() { return releaseId; }
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }
    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }
    public String getLinesJson() { return linesJson; }
    public void setLinesJson(String linesJson) { this.linesJson = linesJson; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Date getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Date nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
