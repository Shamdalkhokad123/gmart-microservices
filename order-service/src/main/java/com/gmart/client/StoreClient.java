package com.gmart.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.web.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** store-service's internal API: is the store taking orders, and on what terms. */
@Component
public class StoreClient {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StoreInfo(Integer id, String name, String status, boolean open, boolean acceptingOrders,
                            BigDecimal commissionPercent, String razorpayAccountId) {
    }

    private final InternalClient http;

    public StoreClient(@Value("${gmart.services.store-url}") String baseUrl,
                       @Value("${gmart.internal.token}") String token,
                       ObjectMapper objectMapper) {
        this.http = new InternalClient("Store service", baseUrl, token, objectMapper);
    }

    public StoreInfo get(Integer storeId) {
        return http.get("/internal/stores/{id}", new ParameterizedTypeReference<StoreInfo>() {}, storeId);
    }

    /** Throws a customer-facing 409 unless the store is approved and open. */
    public StoreInfo requireAcceptingOrders(Integer storeId) {
        if (storeId == null) {
            throw new BusinessException("Your cart has no store. Please add the items again.", 409);
        }
        StoreInfo s = get(storeId);
        if (!s.acceptingOrders()) {
            throw new BusinessException(s.name() + (s.open() ? " isn't taking orders right now" : " is closed right now"), 409);
        }
        return s;
    }
}
