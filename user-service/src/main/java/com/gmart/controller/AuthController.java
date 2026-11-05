package com.gmart.controller;

import com.gmart.web.Errors;
import com.gmart.dto.ApiResponse;
import com.gmart.dto.AuthResponse;
import com.gmart.dto.LoginRequest;
import com.gmart.dto.RegisterRequest;
import com.gmart.entity.User;
import com.gmart.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private com.gmart.web.RateLimiter limiter;

    /** Failed logins allowed per account, and login attempts per IP, in this window. */
    private static final java.time.Duration LOGIN_WINDOW = java.time.Duration.ofMinutes(15);
    private static final int MAX_FAILURES_PER_ACCOUNT = 5;
    private static final int MAX_ATTEMPTS_PER_IP = 30;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           jakarta.servlet.http.HttpServletRequest http) {
        String accountKey = "login-fail:" + request.getUsername().trim().toLowerCase(java.util.Locale.ROOT);
        String ipKey = "login-ip:" + com.gmart.web.RateLimiter.clientIp(http);
        if (limiter.count(accountKey, LOGIN_WINDOW) >= MAX_FAILURES_PER_ACCOUNT) {
            return ResponseEntity.status(429).body(ApiResponse.error(
                    "Too many failed attempts for this account. Please try again in 15 minutes."));
        }
        try {
            limiter.acquire(ipKey, MAX_ATTEMPTS_PER_IP, LOGIN_WINDOW, "Too many login attempts. Please try again later.");
            AuthResponse response = authService.login(request);
            limiter.reset(accountKey);
            return ResponseEntity.ok(ApiResponse.success("Login successful", response));
        } catch (com.gmart.web.BusinessException e) {
            if (e.getStatus() != 429) {
                limiter.record(accountKey);
            }
            return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    public record PasswordChange(String currentPassword, String newPassword) {
    }

    /** Change your own password (logged in; wrong current passwords count towards the lockout). */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(org.springframework.security.core.Authentication auth,
                                                            @RequestBody PasswordChange body) {
        String key = "login-fail:" + auth.getName().toLowerCase(java.util.Locale.ROOT);
        if (limiter.count(key, LOGIN_WINDOW) >= MAX_FAILURES_PER_ACCOUNT) {
            return ResponseEntity.status(429).body(ApiResponse.error("Too many failed attempts. Please try again in 15 minutes."));
        }
        try {
            authService.changePassword(auth.getName(), body.currentPassword(), body.newPassword());
            return ResponseEntity.ok(ApiResponse.success("Password changed"));
        } catch (com.gmart.web.BusinessException e) {
            if (e.getMessage().startsWith("Your current password")) {
                limiter.record(key);
            }
            return ResponseEntity.status(e.getStatus()).body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<User>> register(@Valid @RequestBody RegisterRequest request,
                                                      jakarta.servlet.http.HttpServletRequest http) {
        try {
            limiter.acquire("register-ip:" + com.gmart.web.RateLimiter.clientIp(http), 10, java.time.Duration.ofHours(1),
                    "Too many sign-ups from your network. Please try again later.");
            User registeredUser = authService.register(request);
            return ResponseEntity.ok(ApiResponse.success("User registered successfully", registeredUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(Errors.userMessage(e)));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(ApiResponse.error("Unauthorized"));
        }
        User user = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Current user fetched", user));
    }
}
