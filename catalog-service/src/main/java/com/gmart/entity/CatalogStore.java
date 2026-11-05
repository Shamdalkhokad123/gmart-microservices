package com.gmart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * catalog-service's copy of the stores it needs: name and whether customers may see the
 * store's products. store-service keeps it up to date (approve, suspend, rename).
 */
@Entity
@Table(name = "stores")
public class CatalogStore {

    @Id
    private Integer id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private boolean active;

    protected CatalogStore() {
    }

    public CatalogStore(Integer id, String name, boolean active) {
        this.id = id;
        this.name = name;
        this.active = active;
    }

    public Integer getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
