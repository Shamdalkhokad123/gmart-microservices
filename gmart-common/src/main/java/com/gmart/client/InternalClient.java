package com.gmart.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gmart.security.InternalApiFilter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Calls another service's /internal/** API. Responses use the ApiResponse
 * envelope; on an error the other service's message is rethrown as a
 * {@link RemoteServiceException} so it can reach the customer unchanged.
 */
public class InternalClient {

    private final RestClient http;
    private final String serviceName;
    private final ObjectMapper objectMapper;

    public InternalClient(String serviceName, String baseUrl, String internalToken, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.serviceName = serviceName;
        this.objectMapper = objectMapper;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(InternalApiFilter.HEADER, internalToken)
                .build();
    }

    /** GET returning the "data" field of the ApiResponse. */
    public <T> T get(String uri, ParameterizedTypeReference<T> type, Object... uriVars) {
        return call(() -> unwrap(http.get().uri(uri, uriVars).retrieve().body(JsonNode.class), type));
    }

    /** POST returning the "data" field of the ApiResponse. */
    public <T> T post(String uri, Object body, ParameterizedTypeReference<T> type, Object... uriVars) {
        return call(() -> unwrap(http.post().uri(uri, uriVars).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class), type));
    }

    private <T> T unwrap(JsonNode envelope, ParameterizedTypeReference<T> type) {
        if (envelope == null || envelope.path("data").isMissingNode() || envelope.path("data").isNull()) {
            return null;
        }
        return objectMapper.convertValue(envelope.get("data"), objectMapper.getTypeFactory().constructType(type.getType()));
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            String message = null;
            try {
                message = objectMapper.readTree(e.getResponseBodyAsString()).path("message").asText(null);
            } catch (Exception ignored) {
                // not an ApiResponse body
            }
            throw new RemoteServiceException(message != null ? message : serviceName + " returned " + e.getStatusCode().value(),
                    e.getStatusCode().value(), e);
        } catch (ResourceAccessException e) {
            throw new RemoteServiceException(serviceName + " is unavailable. Please try again in a moment.", 503, e);
        }
    }

    public static class RemoteServiceException extends RuntimeException {
        private final int status;

        public RemoteServiceException(String message, int status, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
