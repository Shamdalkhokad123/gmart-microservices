package com.gmart.entity;

import jakarta.persistence.*;

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

    /** Selling price. */
    @Column(nullable = false)
    private float price;

    /** Maximum retail price (shown struck-through when higher than price). */
    private Float mrp;

    /** Pack size shown to customers, e.g. "1 kg", "500 ml", "Pack of 6". */
    private String unit;

    /** Units available in the store. */
    private Integer stock;

    private String imageUrl;

    public Product() {
    }

    public Product(Integer productId, String productName, String productDesc, String productCategory, float price, String imageUrl) {
        this.productId = productId;
        this.productName = productName;
        this.productDesc = productDesc;
        this.productCategory = productCategory;
        this.price = price;
        this.imageUrl = imageUrl;
    }

    public Product(String productName, String productDesc, String productCategory, float price, Float mrp, String unit, Integer stock, String imageUrl) {
        this(null, productName, productDesc, productCategory, price, imageUrl);
        this.mrp = mrp;
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
    public float getPrice() { return price; }
    public void setPrice(float price) { this.price = price; }
    public Float getMrp() { return mrp; }
    public void setMrp(Float mrp) { this.mrp = mrp; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
