package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.Store;
import com.gmart.service.StoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Service-to-service (X-Internal-Token): order-service and payment-service check a store before taking an order. */
@RestController
@RequestMapping("/internal/stores")
public class InternalStoreController {

    /** acceptingOrders = approved and open. */
    public record StoreInfo(Integer id, String name, String status, boolean open, boolean acceptingOrders,
                            BigDecimal commissionPercent, String razorpayAccountId, String ownerUsername) {
    }

    private final StoreService service;

    public InternalStoreController(StoreService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoreInfo>> get(@PathVariable Integer id) {
        Store s = service.find(id);
        return ResponseEntity.ok(ApiResponse.success("Store", new StoreInfo(s.getId(), s.getName(), s.getStatus().name(),
                s.isOpen(), s.isVisible() && s.isOpen(), s.getCommissionPercent(), s.getRazorpayAccountId(), s.getOwnerUsername())));
    }
}
