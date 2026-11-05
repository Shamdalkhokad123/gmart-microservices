package com.gmart.service;

import com.gmart.web.BusinessException;
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

    private final com.gmart.client.StoreClient storeClient;

    public CartService(CartItemRepository cartItemRepository, CatalogClient catalog, PricingService pricingService,
                       com.gmart.client.StoreClient storeClient) {
        this.storeClient = storeClient;
        this.cartItemRepository = cartItemRepository;
        this.catalog = catalog;
        this.pricingService = pricingService;
    }

    public CartSummaryDto getCartForUser(String username, String couponCode) {
        List<CartItem> items = cartItemRepository.findByUserNameAndPaymentStatus(username, "NP");
        int totalItems = items.stream().mapToInt(CartItem::getQuantity).sum();
        CartSummaryDto dto = new CartSummaryDto(items, pricingService.calculate(items, couponCode, username), totalItems);
        if (!items.isEmpty()) {
            dto.setStoreId(items.get(0).getStoreId());
            dto.setStoreName(storeName(items.get(0).getStoreId()));
        }
        return dto;
    }

    /** Store names (cart header and messages); filled from products added and store-service. */
    private final java.util.Map<Integer, String> storeNames = new java.util.concurrent.ConcurrentHashMap<>();

    private String storeName(Integer storeId) {
        if (storeId == null) return "another store";
        return storeNames.computeIfAbsent(storeId, id -> {
            try {
                return storeClient.get(id).name();
            } catch (RuntimeException e) {
                return "another store";
            }
        });
    }

    /**
     * One store per cart. Adding from a different store fails with 409 unless
     * {@code replaceCart} is true, in which case the old cart is emptied first.
     */
    @Transactional
    public CartItem addToCart(String username, Integer productId, int quantity, boolean replaceCart) {
        ProductInfo product = catalog.product(productId);
        if (product.storeId() == null) {
            throw new BusinessException("This product isn't available");
        }
        if (product.storeName() != null) {
            storeNames.put(product.storeId(), product.storeName());
        }
        List<CartItem> cart = cartItemRepository.findByUserNameAndPaymentStatus(username, "NP");
        CartItem other = cart.stream().filter(i -> !product.storeId().equals(i.getStoreId())).findFirst().orElse(null);
        if (other != null) {
            if (!replaceCart) {
                String current = storeName(other.getStoreId());
                throw new BusinessException("Your cart has items from " + current + ". Start a new cart to shop from "
                        + (product.storeName() == null ? "this store" : product.storeName()) + "?", 409);
            }
            cartItemRepository.deleteAll(cart);
            cartItemRepository.flush();
        }

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
        cartItem.setStoreId(product.storeId());
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
                .orElseThrow(() -> new BusinessException("Cart item not found"));
        if (!username.equals(item.getUserName()) || !"NP".equals(item.getPaymentStatus())) {
            throw new BusinessException("Cart item not found");
        }
        return item;
    }

    private void checkAvailability(ProductInfo product, int requestedQty) {
        int stock = product.stock() == null ? Integer.MAX_VALUE : product.stock();
        if (stock <= 0) {
            throw new BusinessException(product.productName() + " is out of stock");
        }
        if (requestedQty > MAX_QTY_PER_ITEM) {
            throw new BusinessException("You can add up to " + MAX_QTY_PER_ITEM + " units of an item");
        }
        if (requestedQty > stock) {
            throw new BusinessException("Only " + stock + " left in stock");
        }
    }
}
