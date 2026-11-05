package com.gmart.service;

import com.gmart.config.CacheConfig;
import com.gmart.web.BusinessException;
import com.gmart.dto.StockLine;
import com.gmart.entity.Product;
import com.gmart.entity.ProcessedStockRelease;
import com.gmart.repository.ProcessedStockReleaseRepository;
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
    private final ProcessedStockReleaseRepository processedReleases;
    private final com.gmart.repository.CatalogStoreRepository stores;

    public StockService(ProductRepository productRepository, ProcessedStockReleaseRepository processedReleases,
                        com.gmart.repository.CatalogStoreRepository stores) {
        this.productRepository = productRepository;
        this.processedReleases = processedReleases;
        this.stores = stores;
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
                    .orElseThrow(() -> new BusinessException(name(line) + " is no longer available", 409));
            if (product.getStoreId() == null || !stores.findById(product.getStoreId()).map(s -> s.isActive()).orElse(false)) {
                throw new BusinessException(product.getProductName() + "'s store isn't taking orders right now", 409);
            }
            int stock = product.getStock() == null ? Integer.MAX_VALUE : product.getStock();
            if (stock < line.quantity()) {
                throw new BusinessException(stock <= 0
                        ? product.getProductName() + " just went out of stock"
                        : "Only " + stock + " of " + product.getProductName() + " left", 409);
            }
            if (product.getStock() != null) {
                product.setStock(stock - line.quantity());
            }
            result.add(product);
        }
        return result;
    }

    /**
     * Puts stock back (cancelled order, or a checkout that failed after reserving).
     * Idempotent per {@code releaseId}: the caller retries until it gets an answer, and a
     * release that was already applied is not applied again. Returns false for a repeat.
     */
    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public boolean release(String releaseId, List<StockLine> lines) {
        if (releaseId == null || releaseId.isBlank()) {
            throw new BusinessException("releaseId is required");
        }
        if (processedReleases.existsById(releaseId)) {
            return false;
        }
        // Inserting first takes the primary-key lock, so two concurrent retries can't both apply
        processedReleases.saveAndFlush(new ProcessedStockRelease(releaseId));
        for (StockLine line : sorted(lines)) {
            productRepository.lockById(line.productId()).ifPresent(p -> {
                if (p.getStock() != null) {
                    p.setStock(p.getStock() + line.quantity());
                }
            });
        }
        return true;
    }

    private static List<StockLine> sorted(List<StockLine> lines) {
        return lines.stream().sorted(Comparator.comparing(StockLine::productId)).toList();
    }

    private static String name(StockLine line) {
        return line.productName() != null ? line.productName() : "Product " + line.productId();
    }
}
