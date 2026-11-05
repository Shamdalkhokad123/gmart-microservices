package com.gmart.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpayClientTest {

    private static final String SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "test_webhook_secret";

    private final RazorpayClient client = new RazorpayClient("rzp_test_123", SECRET, WEBHOOK_SECRET, "http://localhost:1");

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void acceptsTheSignatureRazorpayCheckoutSends() throws Exception {
        String sig = hmac("order_ABC|pay_XYZ", SECRET);
        assertThat(client.isValidPaymentSignature("order_ABC", "pay_XYZ", sig)).isTrue();
    }

    @Test
    void rejectsForgedOrSwappedSignatures() throws Exception {
        assertThat(client.isValidPaymentSignature("order_ABC", "pay_XYZ", hmac("order_ABC|pay_XYZ", "wrong"))).isFalse();
        // a valid signature for another payment must not verify this one
        assertThat(client.isValidPaymentSignature("order_ABC", "pay_OTHER", hmac("order_ABC|pay_XYZ", SECRET))).isFalse();
        assertThat(client.isValidPaymentSignature("order_ABC", "pay_XYZ", null)).isFalse();
    }

    @Test
    void verifiesWebhookBodies() throws Exception {
        String body = "{\"event\":\"payment.captured\"}";
        assertThat(client.isValidWebhookSignature(body, hmac(body, WEBHOOK_SECRET))).isTrue();
        assertThat(client.isValidWebhookSignature(body + " ", hmac(body, WEBHOOK_SECRET))).isFalse();
    }

    @Test
    void withoutKeysNothingVerifies() throws Exception {
        RazorpayClient unconfigured = new RazorpayClient("", "", "", "http://localhost:1");
        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.isValidPaymentSignature("o", "p", hmac("o|p", "any-key"))).isFalse();
        assertThat(unconfigured.isValidWebhookSignature("{}", "x")).isFalse();
    }
}
