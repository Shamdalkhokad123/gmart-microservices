package com.gmart.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretsCheckTest {

    private static final String STRONG_JWT = "9f2c4e6a8b0d1f3e5a7c9e1b3d5f7a9c0e2b4d6f8a1c3e5b";
    private static final String STRONG_TOKEN = "c1e3a5b7d9f0e2c4a6b8d0f1e3c5";

    private static SecretsCheck check(String jwt, String token, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        return new SecretsCheck(env, jwt, token);
    }

    @Test
    void strongSecretsStart() {
        assertThatCode(() -> check(STRONG_JWT, STRONG_TOKEN).verify()).doesNotThrowAnyException();
    }

    @Test
    void missingSecretsAreRejected() {
        assertThatThrownBy(() -> check("", "").verify())
                .hasMessageContaining("JWT_SECRET is not set")
                .hasMessageContaining("INTERNAL_TOKEN is not set");
    }

    @Test
    void publicDevelopmentValuesAreRejected() {
        assertThatThrownBy(() -> check("GmartSecretKeyForJwtTokenSigningMustBeVeryLongAndSecure2026SuperKey",
                "gmart-internal-dev-token").verify())
                .hasMessageContaining("JWT_SECRET is the public development value")
                .hasMessageContaining("INTERNAL_TOKEN is the public development value");
    }

    @Test
    void shortSecretsAreRejected() {
        assertThatThrownBy(() -> check("short", "alsoshort").verify())
                .hasMessageContaining("JWT_SECRET must be at least 32")
                .hasMessageContaining("INTERNAL_TOKEN must be at least 24");
    }

    @Test
    void devProfileSkipsTheCheck() {
        assertThatCode(() -> check("", "", "dev").verify()).doesNotThrowAnyException();
    }
}
