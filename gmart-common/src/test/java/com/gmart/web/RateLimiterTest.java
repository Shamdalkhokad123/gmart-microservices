package com.gmart.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void allowsUpToTheLimitThenRefuses() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.acquire("k", 3, Duration.ofMinutes(1), "slow down");
        }
        assertThatThrownBy(() -> limiter.acquire("k", 3, Duration.ofMinutes(1), "slow down"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("slow down")
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(429);
    }

    @Test
    void keysAreIndependentAndResettable() {
        RateLimiter limiter = new RateLimiter();
        limiter.record("a");
        limiter.record("a");
        assertThat(limiter.count("a", Duration.ofMinutes(1))).isEqualTo(2);
        assertThat(limiter.count("b", Duration.ofMinutes(1))).isZero();
        limiter.reset("a");
        assertThat(limiter.count("a", Duration.ofMinutes(1))).isZero();
    }

    @Test
    void clientIpPrefersFirstForwardedHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        assertThat(RateLimiter.clientIp(request)).isEqualTo("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        assertThat(RateLimiter.clientIp(request)).isEqualTo("203.0.113.7");
    }
}
