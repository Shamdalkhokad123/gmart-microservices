package com.gmart.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Calls to order-service and user-service internal APIs. */
@Component
public class ServiceClients {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(java.math.BigDecimal toPay, java.math.BigDecimal itemTotal, java.math.BigDecimal couponDiscount,
                        String couponCode, boolean couponApplied, Integer storeId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserContact(String fullName, String email, String mobileNo) {
    }

    public record PaidOrderRequest(String username, String shippingAddr, String couponCode,
                                   String razorpayOrderId, String razorpayPaymentId, long amountPaise) {
    }

    private static final ParameterizedTypeReference<JsonNode> JSON = new ParameterizedTypeReference<>() {};

    private final InternalClient orders;
    private final InternalClient users;
    private final InternalClient stores;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoreInfo(Integer id, String name, boolean acceptingOrders, java.math.BigDecimal commissionPercent,
                            String razorpayAccountId) {
    }

    /** Store terms: commission and Route linked account. */
    public StoreInfo store(Integer storeId) {
        return stores.get("/internal/stores/{id}", new ParameterizedTypeReference<StoreInfo>() {}, storeId);
    }

    public ServiceClients(@Value("${gmart.services.order-url}") String orderUrl,
                          @Value("${gmart.services.user-url}") String userUrl,
                          @Value("${gmart.services.store-url}") String storeUrl,
                          @Value("${gmart.internal.token}") String token,
                          ObjectMapper objectMapper) {
        this.orders = new InternalClient("Order service", orderUrl, token, objectMapper);
        this.users = new InternalClient("User service", userUrl, token, objectMapper);
        this.stores = new InternalClient("Store service", storeUrl, token, objectMapper);
    }

    /** Server-side total of the customer's cart (throws with the reason if it can't be ordered). */
    public Quote quote(String username, String couponCode) {
        return orders.post("/internal/orders/quote", Map.of("username", username, "couponCode", couponCode == null ? "" : couponCode),
                new ParameterizedTypeReference<Quote>() {});
    }

    /** Creates the order for a captured payment; returns the order JSON as-is for the browser. */
    public JsonNode createPaidOrder(PaidOrderRequest request) {
        return orders.post("/internal/orders/from-payment", request, JSON);
    }

    public JsonNode getOrder(Integer orderId) {
        return orders.get("/internal/orders/{id}", JSON, orderId);
    }

    /** Name, email and phone to prefill Razorpay Checkout; null when unavailable. */
    public UserContact userContact(String username) {
        try {
            return users.get("/internal/users/{username}", new ParameterizedTypeReference<UserContact>() {}, username);
        } catch (RuntimeException e) {
            return null; // prefill is optional
        }
    }
}
