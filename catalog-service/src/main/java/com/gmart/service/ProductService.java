package com.gmart.service;

import com.gmart.config.CacheConfig;
import com.gmart.entity.Product;
import com.gmart.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Cacheable(CacheConfig.PRODUCTS)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Cacheable(CacheConfig.PRODUCT_BY_ID)
    public Product getProductById(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with ID: " + id));
    }

    @Cacheable(cacheNames = CacheConfig.PRODUCTS_BY_CATEGORY, key = "#category == null ? 'All' : #category.toLowerCase()")
    public List<Product> getProductsByCategory(String category) {
        if (category == null || category.trim().isEmpty() || "All".equalsIgnoreCase(category)) {
            return getAllProducts();
        }
        return productRepository.findByProductCategoryIgnoreCase(category);
    }

    public List<Product> searchProducts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllProducts();
        }
        return productRepository.findByProductNameContainingIgnoreCaseOrProductDescContainingIgnoreCase(query, query);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public Product updateProduct(Integer id, Product updated) {
        Product existing = getProductById(id);
        existing.setProductName(updated.getProductName());
        existing.setProductDesc(updated.getProductDesc());
        existing.setProductCategory(updated.getProductCategory());
        existing.setPrice(updated.getPrice());
        existing.setMrp(updated.getMrp());
        existing.setUnit(updated.getUnit());
        existing.setStock(updated.getStock());
        if (updated.getImageUrl() != null && !updated.getImageUrl().trim().isEmpty()) {
            existing.setImageUrl(updated.getImageUrl());
        }
        return productRepository.save(existing);
    }

    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public void deleteProduct(Integer id) {
        productRepository.deleteById(id);
    }
}
