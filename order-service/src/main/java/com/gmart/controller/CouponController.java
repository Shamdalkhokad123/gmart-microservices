package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.service.PricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Public list of offers plus the store's fee rules, for the UI. */
@RestController
@RequestMapping("/coupons")
public class CouponController {

    private final PricingService pricingService;

    public CouponController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PricingService.Coupon>>> list() {
        return ResponseEntity.ok(ApiResponse.success("Coupons", pricingService.getCoupons()));
    }

    @GetMapping("/store-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> storeInfo() {
        return ResponseEntity.ok(ApiResponse.success("Store info", Map.of(
                "freeDeliveryThreshold", PricingService.FREE_DELIVERY_THRESHOLD,
                "deliveryFee", PricingService.DELIVERY_FEE,
                "handlingFee", PricingService.HANDLING_FEE,
                "deliveryMinutes", com.gmart.service.OrderService.PROMISED_MINUTES)));
    }
}
