package com.gmart.security;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start outside the dev profile when the shared secrets are missing,
 * too short, or still the public development values from this repository.
 * (Anyone could sign an admin JWT with a known secret.)
 */
@Component
public class SecretsCheck {

    /** Development values committed in application-dev.properties. Never valid in production. */
    static final List<String> KNOWN_DEV_VALUES = List.of(
            "GmartSecretKeyForJwtTokenSigningMustBeVeryLongAndSecure2026SuperKey",
            "gmart-dev-jwt-secret-not-for-production-0123456789abcdef",
            "gmart-internal-dev-token");

    private final Environment environment;
    private final String jwtSecret;
    private final String internalToken;

    public SecretsCheck(Environment environment,
                        @Value("${gmart.app.jwtSecret:}") String jwtSecret,
                        @Value("${gmart.internal.token:}") String internalToken) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.internalToken = internalToken;
    }

    @PostConstruct
    void verify() {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }
        List<String> problems = new ArrayList<>();
        check("JWT_SECRET", jwtSecret, 32, problems);
        check("INTERNAL_TOKEN", internalToken, 24, problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe configuration: " + String.join("; ", problems)
                    + ". Set strong random values (e.g. 48+ random characters), or run with the 'dev' profile locally.");
        }
    }

    private static void check(String name, String value, int minBytes, List<String> problems) {
        if (value == null || value.isBlank()) {
            problems.add(name + " is not set");
        } else if (KNOWN_DEV_VALUES.contains(value)) {
            problems.add(name + " is the public development value");
        } else if (value.getBytes(StandardCharsets.UTF_8).length < minBytes) {
            problems.add(name + " must be at least " + minBytes + " characters");
        }
    }
}
