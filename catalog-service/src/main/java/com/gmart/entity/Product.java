package com.gmart.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer productId;

    @Column(nullable = false)
    private String productName;

    @Column(length = 1000)
    private String productDesc;

    @Column(nullable = false)
    private String productCategory;

    /** Selling price, in rupees (exact to the paisa). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Maximum retail price (shown struck-through when higher than price). */
    @Column(precision = 10, scale = 2)
    private BigDecimal mrp;

    /** Pack size shown to customers, e.g. "1 kg", "500 ml", "Pack of 6". */
    private String unit;

    /** Units available in the store. */
    private Integer stock;

    private String imageUrl;

    /** The store selling this product (store-service id). */
    @Column(name = "store_id")
    private Integer storeId;

    /** Filled in for responses; not stored. */
    @Transient
    private String storeName;

    public Product() {
    }

    public Product(Integer productId, String productName, String productDesc, String productCategory, BigDecimal price, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.productDesc = productDesc;
        this.productCategory = productCategory;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    /** Convenience for seed data: whole-rupee prices. */
    public Product(String productName, String productDesc, String productCategory, int price, Float mrp, String unit, Integer stock, String imageUrl) {
        this(null, productName, productDesc, productCategory, BigDecimal.valueOf(price), imageUrl);
        this.mrp = mrp == null ? null : new BigDecimal(Float.toString(mrp));
        this.unit = unit;
        this.stock = stock;
    }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductDesc() { return productDesc; }
    public void setProductDesc(String productDesc) { this.productDesc = productDesc; }
    public String getProductCategory() { return productCategory; }
    public void setProductCategory(String productCategory) { this.productCategory = productCategory; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getMrp() { return mrp; }
    public void setMrp(BigDecimal mrp) { this.mrp = mrp; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Integer getStoreId() { return storeId; }
    public void setStoreId(Integer storeId) { this.storeId = storeId; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
