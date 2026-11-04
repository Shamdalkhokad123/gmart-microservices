package com.gmart.service;

import com.gmart.config.CacheConfig;
import com.gmart.dto.StockLine;
import com.gmart.entity.Product;
import com.gmart.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Stock changes requested by order-service at checkout and cancellation. */
@Service
public class StockService {

    private final ProductRepository productRepository;

    public StockService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * All-or-nothing: either every line is reserved or nothing changes.
     * Returns the products at their current price, which is what the customer is charged.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public List<Product> reserve(List<StockLine> lines) {
        List<Product> result = new ArrayList<>();
        // Lock rows in a fixed order so two concurrent checkouts cannot deadlock
        for (StockLine line : sorted(lines)) {
            Product product = productRepository.lockById(line.productId())
                    .orElseThrow(() -> new IllegalStateException(name(line) + " is no longer available"));
            int stock = product.getStock() == null ? Integer.MAX_VALUE : product.getStock();
            if (stock < line.quantity()) {
                throw new IllegalStateException(stock <= 0
                        ? product.getProductName() + " just went out of stock"
                        : "Only " + stock + " of " + product.getProductName() + " left");
            }
            if (product.getStock() != null) {
                product.setStock(stock - line.quantity());
            }
            result.add(product);
        }
        return result;
    }

    /** Puts stock back (cancelled order, or a checkout that failed after reserving). */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public void release(List<StockLine> lines) {
        for (StockLine line : sorted(lines)) {
            productRepository.lockById(line.productId()).ifPresent(p -> {
                if (p.getStock() != null) {
                    p.setStock(p.getStock() + line.quantity());
                }
            });
        }
    }

    private static List<StockLine> sorted(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }

    private static String name(StockLine line) {
        return line.productName() != null ? line.productName() : "Product " + line.productId();
    }
}
