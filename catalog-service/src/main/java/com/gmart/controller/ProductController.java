package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.entity.Product;
import com.gmart.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        List<Product> products;
        if (search != null && !search.trim().isEmpty()) {
            products = productService.searchProducts(search.trim());
        } else if (category != null && !category.trim().isEmpty()) {
            products = productService.getProductsByCategory(category.trim());
        } else {
            products = productService.getAllProducts();
        }
        return ResponseEntity.ok(ApiResponse.success("Products retrieved", products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Integer id) {
        try {
            Product product = productService.getProductById(id);
            return ResponseEntity.ok(ApiResponse.success("Product retrieved", product));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@RequestBody Product product) {
        try {
            Product created = productService.createProduct(product);
            return ResponseEntity.ok(ApiResponse.success("Product created successfully", created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(@PathVariable Integer id, @RequestBody Product product) {
        try {
            Product updated = productService.updateProduct(id, product);
            return ResponseEntity.ok(ApiResponse.success("Product updated successfully", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Integer id) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }
}
