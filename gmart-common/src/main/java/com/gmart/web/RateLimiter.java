package com.gmart.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window counters for abuse protection (login guessing, sign-up and contact spam).
 * In memory, per instance: with N instances the effective limit is up to N times higher,
 * which is still enough to stop password guessing. Move to Redis if that ever matters.
 */
@Component
public class RateLimiter {

    private static final long MAX_WINDOW_MS = Duration.ofHours(1).toMillis();
    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    /** Number of events recorded for {@code key} within {@code window}. */
    public int count(String key, Duration window) {
        Deque<Long> q = events.get(key);
        if (q == null) return 0;
        long cutoff = System.currentTimeMillis() - window.toMillis();
        synchronized (q) {
            return (int) q.stream().filter(t -> t > cutoff).count();
        }
    }

    public void record(String key) {
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
            while (q.size() > 1000) q.pollFirst();
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    /**
     * Records one event and throws 429 if that makes more than {@code max} within {@code window}.
     */
    public void acquire(String key, int max, Duration window, String message) {
        if (count(key, window) >= max) {
            throw new BusinessException(message, 429);
        }
        record(key);
    }

    /** The caller's IP: first X-Forwarded-For hop (set by the gateway/proxy), else the socket address. */
    public static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Drops old entries so memory doesn't grow. */
    @Scheduled(fixedDelay = 600_000)
    void cleanUp() {
        long cutoff = System.currentTimeMillis() - MAX_WINDOW_MS;
        events.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                e.getValue().removeIf(t -> t <= cutoff);
                return e.getValue().isEmpty();
            }
        });
    }
}
