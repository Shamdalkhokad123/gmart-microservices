package com.gmart.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * Issues (user-service) and validates (every service) the login JWT.
 * All services share the same secret, so none of them needs the user database:
 * the username is the subject and the role travels in the "role" claim.
 */
@Component
public class JwtTokenProvider {

    @Value("${gmart.app.jwtSecret}")
    private String jwtSecret;

    @Value("${gmart.app.jwtExpirationMs:86400000}")
    private long jwtExpirationMs;

    private Key signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtExpirationMs))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Returns the claims, or null when the token is invalid or expired. */
    public Claims parse(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(signingKey()).build().parseClaimsJws(token).getBody();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
