package com.gmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;

/** A stock release that has already been applied (so a retry doesn't add stock twice). */
@Entity
@Table(name = "processed_stock_releases")
public class ProcessedStockRelease {

    @Id
    @Column(length = 64)
    private String releaseId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date createdAt;

    protected ProcessedStockRelease() {
    }

    public ProcessedStockRelease(String releaseId) {
        this.releaseId = releaseId;
        this.createdAt = new Date();
    }

    public String getReleaseId() { return releaseId; }
    public Date getCreatedAt() { return createdAt; }
}
