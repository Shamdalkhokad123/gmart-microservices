package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Service-to-service API (X-Internal-Token). Used by payment-service to prefill Razorpay Checkout. */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> contact(@PathVariable String username) {
        return userRepository.findByUsername(username)
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("username", u.getUsername());
                    m.put("fullName", u.getFullName());
                    m.put("email", u.getEmail());
                    m.put("mobileNo", u.getMobileNo() == null ? null : String.valueOf(u.getMobileNo()));
                    return ResponseEntity.ok(ApiResponse.success("User", m));
                })
                .orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error("User not found")));
    }
}
