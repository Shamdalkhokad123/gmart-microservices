package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.dto.CartSummaryDto;
import com.gmart.entity.CartItem;
import com.gmart.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** Cart with full bill; pass ?coupon=CODE to preview a coupon. */
    @GetMapping
    public ResponseEntity<ApiResponse<CartSummaryDto>> getCart(Authentication authentication,
                                                               @RequestParam(required = false) String coupon) {
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved", cartService.getCartForUser(authentication.getName(), coupon)));
    }

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<CartItem>> addToCart(Authentication authentication,
                                                           @RequestBody Map<String, Object> payload) {
        try {
            Integer productId = Integer.parseInt(payload.get("productId").toString());
            int quantity = payload.containsKey("quantity") ? Integer.parseInt(payload.get("quantity").toString()) : 1;
            boolean replaceCart = Boolean.parseBoolean(String.valueOf(payload.getOrDefault("replaceCart", "false")));
            CartItem item = cartService.addToCart(authentication.getName(), productId, quantity, replaceCart);
            return ResponseEntity.ok(ApiResponse.success("Added to cart", item));
        } catch (com.gmart.web.BusinessException e) {
            // 409 = cart has another store's items; the app then asks "start a new cart?"
            return ResponseEntity.status(e.getStatus() == 409 ? 409 : 400).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @PutMapping("/update/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItem>> updateQuantity(Authentication authentication,
                                                                @PathVariable Integer cartItemId,
                                                                @RequestParam int quantity) {
        try {
            CartItem updated = cartService.updateCartQuantity(authentication.getName(), cartItemId, quantity);
            return ResponseEntity.ok(ApiResponse.success("Cart updated", updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @DeleteMapping("/delete/{cartItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteCartItem(Authentication authentication, @PathVariable Integer cartItemId) {
        try {
            cartService.deleteCartItem(authentication.getName(), cartItemId);
            return ResponseEntity.ok(ApiResponse.success("Item removed from cart"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart(Authentication authentication) {
        cartService.clearCartForUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }
}
