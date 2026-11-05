package com.gmart.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin wrapper over the Razorpay REST API (https://razorpay.com/docs/api/).
 * Keys come from env vars RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET; use test-mode
 * keys (rzp_test_...) while developing.
 */
@Component
public class RazorpayClient {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {};

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final RestClient http;

    public RazorpayClient(@Value("${razorpay.key-id:}") String keyId,
                          @Value("${razorpay.key-secret:}") String keySecret,
                          @Value("${razorpay.webhook-secret:}") String webhookSecret,
                          @Value("${razorpay.api-base-url:https://api.razorpay.com/v1}") String baseUrl) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(h -> {
                    if (isConfigured()) h.setBasicAuth(keyId, keySecret);
                })
                .build();
    }

    public boolean isConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    /** POST /orders. Amount is in paise. Returns the Razorpay order entity. */
    public Map<String, Object> createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        return createOrder(amountPaise, currency, receipt, notes, null, 0);
    }

    /**
     * POST /orders with a Razorpay Route transfer: {@code transferPaise} of the payment goes to the
     * store's linked account ({@code acc_...}) once it is captured; the rest stays with the platform.
     */
    public Map<String, Object> createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes,
                                           String linkedAccountId, long transferPaise) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("currency", currency);
        body.put("receipt", receipt);
        body.put("notes", notes);
        if (linkedAccountId != null && transferPaise > 0) {
            Map<String, Object> transfer = new LinkedHashMap<>();
            transfer.put("account", linkedAccountId);
            transfer.put("amount", transferPaise);
            transfer.put("currency", currency);
            transfer.put("notes", notes);
            transfer.put("on_hold", 0);
            body.put("transfers", java.util.List.of(transfer));
        }
        return post("/orders", body);
    }

    /** GET /payments/{id} */
    public Map<String, Object> fetchPayment(String paymentId) {
        try {
            return http.get().uri("/payments/{id}", paymentId).retrieve().body(JSON_MAP);
        } catch (RestClientException e) {
            throw gatewayError("fetch payment " + paymentId, e);
        }
    }

    /** GET /orders/{id}/payments: every payment attempt made for a Razorpay order. */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> fetchOrderPayments(String razorpayOrderId) {
        try {
            Map<String, Object> body = http.get().uri("/orders/{id}/payments", razorpayOrderId).retrieve().body(JSON_MAP);
            Object items = body == null ? null : body.get("items");
            return items instanceof java.util.List<?> list ? (java.util.List<Map<String, Object>>) list : java.util.List.of();
        } catch (RestClientException e) {
            throw gatewayError("fetch payments of order " + razorpayOrderId, e);
        }
    }

    /** POST /payments/{id}/capture, for accounts where auto-capture is off. */
    public Map<String, Object> capturePayment(String paymentId, long amountPaise, String currency) {
        return post("/payments/" + paymentId + "/capture", Map.of("amount", amountPaise, "currency", currency));
    }

    /** POST /payments/{id}/refund (full refund of the given amount). */
    public Map<String, Object> refund(String paymentId, long amountPaise) {
        return refund(paymentId, amountPaise, false);
    }

    /** {@code reverseTransfers}: also take back the store's Route transfer (reverse_all). */
    public Map<String, Object> refund(String paymentId, long amountPaise, boolean reverseTransfers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", amountPaise);
        body.put("speed", "normal");
        if (reverseTransfers) {
            body.put("reverse_all", 1);
        }
        return post("/payments/" + paymentId + "/refund", body);
    }

    /** Checkout handler signature: HMAC_SHA256(order_id + "|" + payment_id, key_secret). */
    public boolean isValidPaymentSignature(String razorpayOrderId, String razorpayPaymentId, String signature) {
        if (!isConfigured() || signature == null) return false;
        return constantTimeEquals(hmacSha256Hex(razorpayOrderId + "|" + razorpayPaymentId, keySecret), signature);
    }

    /** Webhook signature: HMAC_SHA256(raw request body, webhook_secret). */
    public boolean isValidWebhookSignature(String rawBody, String signature) {
        if (webhookSecret.isBlank() || signature == null) return false;
        return constantTimeEquals(hmacSha256Hex(rawBody, webhookSecret), signature);
    }

    private Map<String, Object> post(String path, Object body) {
        try {
            return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JSON_MAP);
        } catch (RestClientException e) {
            throw gatewayError("POST " + path, e);
        }
    }

    private static RuntimeException gatewayError(String what, RestClientException e) {
        String detail = e instanceof RestClientResponseException r ? r.getResponseBodyAsString() : e.getMessage();
        log.error("Razorpay {} failed: {}", what, detail);
        return new PaymentGatewayException("Payment gateway error. Please try again in a moment.", e);
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not compute HMAC", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Customer-safe message; the gateway's raw response is only logged. */
    public static class PaymentGatewayException extends com.gmart.web.BusinessException {
        public PaymentGatewayException(String message, Throwable cause) {
            super(message, 502, cause);
        }
    }
}
