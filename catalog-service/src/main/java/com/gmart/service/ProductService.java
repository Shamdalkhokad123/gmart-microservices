package com.gmart.service;

import com.gmart.config.CacheConfig;
import com.gmart.entity.CatalogStore;
import com.gmart.entity.Product;
import com.gmart.repository.CatalogStoreRepository;
import com.gmart.repository.ProductRepository;
import com.gmart.security.CurrentUser;
import com.gmart.web.BusinessException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Products per store. Customers only see products of active (approved, not suspended) stores.
 * Store owners and staff manage their own store's products; the super admin can manage any.
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final CatalogStoreRepository stores;

    public ProductService(ProductRepository products, CatalogStoreRepository stores) {
        this.products = products;
        this.stores = stores;
    }

    // ---------------- customers ----------------

    /** Visible products, optionally of one store / one category. Cached (search isn't). */
    @Cacheable(cacheNames = CacheConfig.PRODUCTS,
            key = "'s' + (#storeId == null ? 'all' : #storeId) + ':' + (#category == null ? 'all' : #category.toLowerCase())")
    public List<Product> visible(Integer storeId, String category) {
        return withStoreNames(products.findVisible(storeId, blankToNull(category), null));
    }

    public List<Product> search(Integer storeId, String query) {
        return withStoreNames(products.findVisible(storeId, null, blankToNull(query)));
    }

    @Cacheable(CacheConfig.PRODUCT_BY_ID)
    public Product visibleProduct(Integer id) {
        Product p = products.findVisibleById(id).orElseThrow(() -> new BusinessException("Product not found", 404));
        return withStoreNames(List.of(p)).get(0);
    }

    // ---------------- store console ----------------

    /** Owner/staff: their store's products (including hidden ones). Super admin: any store. */
    public List<Product> manageList(CurrentUser me, Integer storeId) {
        Integer store = me.isSuperAdmin() ? storeId : me.requireStore();
        if (store == null) {
            throw new BusinessException("Choose a store");
        }
        me.requireManage(store);
        return withStoreNames(products.findByStoreIdOrderByProductIdAsc(store));
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public Product create(CurrentUser me, Product product) {
        // owners and staff always create in their own store; the super admin names the store
        Integer store = me.isSuperAdmin() ? product.getStoreId() : me.requireStore();
        if (store == null || !stores.existsById(store)) {
            throw new BusinessException("Choose an existing store for this product");
        }
        me.requireManage(store);
        validate(product);
        product.setProductId(null);
        product.setStoreId(store);
        return products.save(product);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public Product update(CurrentUser me, Integer id, Product updated) {
        Product existing = products.findById(id).orElseThrow(() -> new BusinessException("Product not found", 404));
        me.requireManage(existing.getStoreId());
        validate(updated);
        existing.setProductName(updated.getProductName());
        existing.setProductDesc(updated.getProductDesc());
        existing.setProductCategory(updated.getProductCategory());
        existing.setPrice(updated.getPrice());
        existing.setMrp(updated.getMrp());
        existing.setUnit(updated.getUnit());
        existing.setStock(updated.getStock());
        if (updated.getImageUrl() != null && !updated.getImageUrl().isBlank()) {
            existing.setImageUrl(updated.getImageUrl());
        }
        return products.save(existing);
    }

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public void delete(CurrentUser me, Integer id) {
        Product existing = products.findById(id).orElseThrow(() -> new BusinessException("Product not found", 404));
        me.requireManage(existing.getStoreId());
        products.delete(existing);
    }

    // ---------------- store-service sync ----------------

    @Transactional
    @CacheEvict(cacheNames = {CacheConfig.PRODUCTS, CacheConfig.PRODUCT_BY_ID, CacheConfig.PRODUCTS_BY_CATEGORY}, allEntries = true)
    public void syncStore(Integer id, String name, boolean active) {
        CatalogStore s = stores.findById(id).orElseGet(() -> new CatalogStore(id, name, active));
        s.setName(name);
        s.setActive(active);
        stores.save(s);
    }

    // ---------------- helpers ----------------

    public List<Product> withStoreNames(List<Product> list) {
        Map<Integer, String> names = stores.findAll().stream()
                .collect(Collectors.toMap(CatalogStore::getId, CatalogStore::getName, (a, b) -> a));
        list.forEach(p -> p.setStoreName(names.get(p.getStoreId())));
        return list;
    }

    private static void validate(Product p) {
        if (p.getProductName() == null || p.getProductName().isBlank() || p.getProductCategory() == null || p.getProductCategory().isBlank()) {
            throw new BusinessException("Product name and category are required");
        }
        if (p.getPrice() == null || p.getPrice().signum() <= 0) {
            throw new BusinessException("Price must be more than ₹0");
        }
        if (p.getMrp() != null && p.getMrp().compareTo(p.getPrice()) < 0) {
            throw new BusinessException("MRP can't be lower than the selling price");
        }
        if (p.getStock() != null && p.getStock() < 0) {
            throw new BusinessException("Stock can't be negative");
        }
        if (p.getPrice().compareTo(new BigDecimal("1000000")) >= 0) {
            throw new BusinessException("Price is too high");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || "all".equalsIgnoreCase(s.trim()) ? null : s.trim();
    }
}
