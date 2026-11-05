package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.dto.StockLine;
import com.gmart.entity.Product;
import com.gmart.repository.ProductRepository;
import com.gmart.service.StockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Service-to-service API (X-Internal-Token), used by order-service. */
@RestController
@RequestMapping("/internal")
public class InternalCatalogController {

    private final ProductRepository productRepository;
    private final StockService stockService;
    private final com.gmart.service.ProductService productService;

    public InternalCatalogController(ProductRepository productRepository, StockService stockService,
                                     com.gmart.service.ProductService productService) {
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.productService = productService;
    }

    /** Current price, MRP and stock for the given products (not cached). */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<Product>>> products(@RequestParam List<Integer> ids) {
        return ResponseEntity.ok(ApiResponse.success("Products", productRepository.findAllById(ids)));
    }

    @PostMapping("/stock/reserve")
    public ResponseEntity<ApiResponse<List<Product>>> reserve(@RequestBody List<StockLine> lines) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Reserved", stockService.reserve(lines)));
        } catch (com.gmart.web.BusinessException e) {
            return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
        }
    }

    public record StoreSync(String name, boolean active) {
    }

    /** store-service: a store was approved, renamed, suspended or reactivated. */
    @PutMapping("/stores/{id}")
    public ResponseEntity<ApiResponse<Void>> syncStore(@PathVariable Integer id, @RequestBody StoreSync body) {
        productService.syncStore(id, body.name(), body.active());
        return ResponseEntity.ok(ApiResponse.success("Store synced"));
    }

    /** {@code releaseId} makes retries safe: the same release is only applied once. */
    public record ReleaseRequest(String releaseId, List<StockLine> lines) {
    }

    @PostMapping("/stock/release")
    public ResponseEntity<ApiResponse<Void>> release(@RequestBody ReleaseRequest request) {
        boolean applied = stockService.release(request.releaseId(), request.lines());
        return ResponseEntity.ok(ApiResponse.success(applied ? "Released" : "Already released"));
    }
}
