package com.gmart.client;

import com.gmart.web.BusinessException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** catalog-service's internal API: product details and stock. */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final ParameterizedTypeReference<List<ProductInfo>> PRODUCTS = new ParameterizedTypeReference<>() {};

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductInfo(Integer productId, String productName, java.math.BigDecimal price, java.math.BigDecimal mrp,
                              String unit, Integer stock, String imageUrl, Integer storeId, String storeName) {
    }

    public record StockLine(Integer productId, String productName, int quantity) {
    }

    private final InternalClient http;

    public CatalogClient(@Value("${gmart.services.catalog-url}") String baseUrl,
                         @Value("${gmart.internal.token}") String token,
                         ObjectMapper objectMapper) {
        this.http = new InternalClient("Catalog service", baseUrl, token, objectMapper);
    }

    public List<ProductInfo> products(Collection<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        String csv = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<ProductInfo> result = http.get("/internal/products?ids={ids}", PRODUCTS, csv);
        return result == null ? List.of() : result;
    }

    public ProductInfo product(Integer id) {
        return products(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new BusinessException("Product not found"));
    }

    /** All-or-nothing; throws with the customer-facing reason when something ran out. */
    public List<ProductInfo> reserve(List<StockLine> lines) {
        return http.post("/internal/stock/reserve", lines, PRODUCTS);
    }

    /**
     * Gives stock back. Throws if catalog-service can't be reached; callers go through
     * StockReleaseService, which retries. Safe to repeat with the same {@code releaseId}.
     */
    public void release(String releaseId, List<StockLine> lines) {
        http.post("/internal/stock/release", java.util.Map.of("releaseId", releaseId, "lines", lines),
                new ParameterizedTypeReference<Void>() {});
    }
}
