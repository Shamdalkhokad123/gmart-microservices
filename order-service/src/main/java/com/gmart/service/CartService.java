package com.gmart.service;

import com.gmart.client.CatalogClient;
import com.gmart.client.CatalogClient.ProductInfo;
import com.gmart.dto.CartSummaryDto;
import com.gmart.entity.CartItem;
import com.gmart.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    /** Per-item limit, like real quick-commerce apps. */
    public static final int MAX_QTY_PER_ITEM = 10;

    private final CartItemRepository cartItemRepository;
    private final CatalogClient catalog;
    private final PricingService pricingService;

    public CartService(CartItemRepository cartItemRepository, CatalogClient catalog, PricingService pricingService) {
        this.cartItemRepository = cartItemRepository;
        this.catalog = catalog;
        this.pricingService = pricingService;
    }

    public CartSummaryDto getCartForUser(String username, String couponCode) {
        List<CartItem> items = cartItemRepository.findByUserNameAndPaymentStatus(username, "NP");
        int totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        return new CartSummaryDto(items, pricingService.calculate(items, couponCode, username), totalItems);
    }

    @Transactional
    public CartItem addToCart(String username, Integer productId, int quantity) {
        ProductInfo product = catalog.product(productId);

        Optional<CartItem> existingOpt = cartItemRepository.findByUserNameAndProductIdAndPaymentStatus(username, productId, "NP");
        int newQty = existingOpt.map(CartItem::getQuantity).orElse(0) + quantity;
        checkAvailability(product, newQty);

        CartItem cartItem = existingOpt.orElseGet(() -> {
            CartItem ci = new CartItem();
            ci.setProductId(productId);
            ci.setUserName(username);
            ci.setPaymentStatus("NP");
            return ci;
        });
        // Always refresh the snapshot so the cart shows current prices
        cartItem.setProductName(product.productName());
        cartItem.setPrice(product.price());
        cartItem.setMrp(product.mrp());
        cartItem.setUnit(product.unit());
        cartItem.setImageUrl(product.imageUrl());
        cartItem.setQuantity(newQty);
        return cartItemRepository.save(cartItem);
    }

    @Transactional
    public CartItem updateCartQuantity(String username, Integer cartItemId, int quantity) {
        CartItem cartItem = findOwnedItem(username, cartItemId);
        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
            return null;
        }
        checkAvailability(catalog.product(cartItem.getProductId()), quantity);
        cartItem.setQuantity(quantity);
        return cartItemRepository.save(cartItem);
    }

    @Transactional
    public void deleteCartItem(String username, Integer cartItemId) {
        cartItemRepository.delete(findOwnedItem(username, cartItemId));
    }

    @Transactional
    public void clearCartForUser(String username) {
        cartItemRepository.deleteAll(cartItemRepository.findByUserNameAndPaymentStatus(username, "NP"));
    }

    private CartItem findOwnedItem(String username, Integer cartItemId) {
        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));
        if (!username.equals(item.getUserName()) || !"NP".equals(item.getPaymentStatus())) {
            throw new RuntimeException("Cart item not found");
        }
        return item;
    }

    private void checkAvailability(ProductInfo product, int requestedQty) {
        int stock = product.stock() == null ? Integer.MAX_VALUE : product.stock();
        if (stock <= 0) {
            throw new RuntimeException(product.productName() + " is out of stock");
        }
        if (requestedQty > MAX_QTY_PER_ITEM) {
            throw new RuntimeException("You can add up to " + MAX_QTY_PER_ITEM + " units of an item");
        }
        if (requestedQty > stock) {
            throw new RuntimeException("Only " + stock + " left in stock");
        }
    }
}
