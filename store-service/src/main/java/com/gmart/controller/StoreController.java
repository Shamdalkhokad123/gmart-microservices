package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.Store;
import com.gmart.security.CurrentUser;
import com.gmart.service.StoreService;
import com.gmart.service.StoreService.StoreForm;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Customers browse stores; anyone signed in can apply; owners manage their store's settings. */
@RestController
@RequestMapping("/stores")
public class StoreController {

    /**
     * What customers see. Seller name, address, GSTIN and FSSAI are shown, as e-commerce
     * rules require; commission, payout account and owner username are not.
     */
    public record PublicStore(Integer id, String name, String description, String legalName, String address,
                              String city, String pincode, String phone, String gstin, String fssai,
                              boolean open, boolean onlinePayments) {
        static PublicStore of(Store s) {
            return new PublicStore(s.getId(), s.getName(), s.getDescription(), s.getLegalName(), s.getAddress(),
                    s.getCity(), s.getPincode(), s.getPhone(), s.getGstin(), s.getFssai(), s.isOpen(), s.isOnlinePayments());
        }
    }

    /** Owner settings: store details plus open/closed. */
    public record OwnerUpdate(StoreForm store, Boolean open) {
    }

    private final StoreService service;

    public StoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicStore>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Stores", service.visibleStores().stream().map(PublicStore::of).toList()));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<PublicStore>> get(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Store", PublicStore.of(service.visibleStore(id))));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<Store>> apply(Authentication auth, @RequestBody StoreForm form) {
        return ResponseEntity.ok(ApiResponse.success("Application received. We'll review it shortly.",
                service.apply(CurrentUser.of(auth), form)));
    }

    /** The signed-in user's applications, newest first (status and review note). */
    @GetMapping("/my-applications")
    public ResponseEntity<ApiResponse<List<Store>>> myApplications(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Applications", service.myApplications(auth.getName())));
    }

    /** Owner or staff: the store they work for. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('ROLE_STORE_OWNER','ROLE_STAFF')")
    public ResponseEntity<ApiResponse<Store>> mine(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Store", service.mine(CurrentUser.of(auth))));
    }

    @PutMapping("/mine")
    @PreAuthorize("hasAuthority('ROLE_STORE_OWNER')")
    public ResponseEntity<ApiResponse<Store>> updateMine(Authentication auth, @RequestBody OwnerUpdate body) {
        return ResponseEntity.ok(ApiResponse.success("Store updated", service.updateMine(CurrentUser.of(auth), body.store(), body.open())));
    }
}
