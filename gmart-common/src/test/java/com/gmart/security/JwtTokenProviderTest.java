package com.gmart.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static JwtTokenProvider provider(String secret, long expiryMs) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "jwtExpirationMs", expiryMs);
        return p;
    }

    @Test
    void tokenCarriesUsernameAndRole() {
        JwtTokenProvider p = provider("a-very-long-test-secret-0123456789abcdef", 60_000);
        Claims claims = p.parse(p.generateToken("asha", "ROLE_ADMIN"));
        assertThat(claims.getSubject()).isEqualTo("asha");
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String forged = provider("attacker-secret-0123456789abcdefghijkl", 60_000).generateToken("asha", "ROLE_ADMIN");
        assertThat(provider("a-very-long-test-secret-0123456789abcdef", 60_000).parse(forged)).isNull();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider p = provider("a-very-long-test-secret-0123456789abcdef", -1_000);
        assertThat(p.parse(p.generateToken("asha", "ROLE_CUSTOMER"))).isNull();
    }

    @Test
    void garbageIsRejected() {
        assertThat(provider("a-very-long-test-secret-0123456789abcdef", 60_000).parse("not.a.jwt")).isNull();
    }
}
