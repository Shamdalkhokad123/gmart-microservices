package com.gmart.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer categoryId;

    @Column(nullable = false, unique = true)
    private String categoryName;

    @Column(length = 500)
    private String categoryDesc;

    private String imageUrl;

    public Category() {
    }

    public Category(Integer categoryId, String categoryName, String categoryDesc) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryDesc = categoryDesc;
    }

    public Category(String categoryName, String categoryDesc, String imageUrl) {
        this.categoryName = categoryName;
        this.categoryDesc = categoryDesc;
        this.imageUrl = imageUrl;
    }

    public Integer getCategoryId() { return categoryId; }
    public void setCategoryId(Integer categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryDesc() { return categoryDesc; }
    public void setCategoryDesc(String categoryDesc) { this.categoryDesc = categoryDesc; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
