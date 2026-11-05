package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.Product;
import com.gmart.security.CurrentUser;
import com.gmart.security.Roles;
import com.gmart.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Customers: products of active stores; filter by store, category or search text. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> list(@RequestParam(required = false) Integer storeId,
                                                           @RequestParam(required = false) String category,
                                                           @RequestParam(required = false) String search) {
        List<Product> products = search != null && !search.isBlank()
                ? productService.search(storeId, search.trim())
                : productService.visible(storeId, category == null ? null : category.trim());
        return ResponseEntity.ok(ApiResponse.success("Products retrieved", products));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Product>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Product retrieved", productService.visibleProduct(id)));
    }

    /** Store console: all of the store's products (owner/staff: own store; super admin: ?storeId=). */
    @GetMapping("/manage")
    @PreAuthorize(Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<List<Product>>> manage(Authentication auth, @RequestParam(required = false) Integer storeId) {
        return ResponseEntity.ok(ApiResponse.success("Products", productService.manageList(CurrentUser.of(auth), storeId)));
    }

    @PostMapping
    @PreAuthorize(Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<Product>> create(Authentication auth, @RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.success("Product created", productService.create(CurrentUser.of(auth), product)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<Product>> update(Authentication auth, @PathVariable Integer id, @RequestBody Product product) {
        return ResponseEntity.ok(ApiResponse.success("Product updated", productService.update(CurrentUser.of(auth), id, product)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.CAN_MANAGE_STORE)
    public ResponseEntity<ApiResponse<Void>> delete(Authentication auth, @PathVariable Integer id) {
        productService.delete(CurrentUser.of(auth), id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }
}
