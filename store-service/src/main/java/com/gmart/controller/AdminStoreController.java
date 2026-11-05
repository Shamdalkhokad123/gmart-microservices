package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.Store;
import com.gmart.service.StoreService;
import com.gmart.service.StoreService.AdminSettings;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Super admin: review applications, approve / reject / suspend / reactivate, commission and payout account. */
@RestController
@RequestMapping("/stores/admin")
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class AdminStoreController {

    public record Note(String note) {
    }

    private final StoreService service;

    public AdminStoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Store>>> list(@RequestParam(required = false) Store.Status status) {
        return ResponseEntity.ok(ApiResponse.success("Stores", service.all(status)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<Store>> approve(Authentication auth, @PathVariable Integer id,
                                                      @RequestBody(required = false) AdminSettings settings) {
        return ResponseEntity.ok(ApiResponse.success("Store approved. The owner gets store access at their next sign-in.",
                service.approve(auth.getName(), id, settings)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<Store>> reject(Authentication auth, @PathVariable Integer id, @RequestBody Note body) {
        return ResponseEntity.ok(ApiResponse.success("Application rejected", service.reject(auth.getName(), id, body.note())));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<Store>> suspend(Authentication auth, @PathVariable Integer id, @RequestBody Note body) {
        return ResponseEntity.ok(ApiResponse.success("Store suspended", service.suspend(auth.getName(), id, body.note())));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<Store>> reactivate(Authentication auth, @PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Store reactivated", service.reactivate(auth.getName(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Store>> update(Authentication auth, @PathVariable Integer id, @RequestBody AdminSettings settings) {
        return ResponseEntity.ok(ApiResponse.success("Store settings saved", service.updateAdmin(auth.getName(), id, settings)));
    }
}
