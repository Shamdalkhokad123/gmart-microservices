package com.gmart.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Map;

/** user-service (make the applicant the owner) and catalog-service (which stores are visible). */
@Component
public class PlatformClients {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final InternalClient users;
    private final InternalClient catalog;

    public PlatformClients(@Value("${gmart.services.user-url}") String userUrl,
                           @Value("${gmart.services.catalog-url}") String catalogUrl,
                           @Value("${gmart.internal.token}") String token,
                           ObjectMapper objectMapper) {
        this.users = new InternalClient("User service", userUrl, token, objectMapper);
        this.catalog = new InternalClient("Catalog service", catalogUrl, token, objectMapper);
    }

    /** The applicant becomes ROLE_STORE_OWNER of this store (takes effect at their next sign-in). */
    public void makeOwner(String username, Integer storeId) {
        users.put("/internal/users/{username}/store-owner", Map.of("storeId", storeId), MAP, username);
    }

    /** Show or hide the store's products in the catalogue. */
    public void syncCatalog(Integer storeId, String name, boolean active) {
        catalog.put("/internal/stores/{id}", Map.of("name", name, "active", active), MAP, storeId);
    }
}
