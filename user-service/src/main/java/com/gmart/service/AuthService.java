package com.gmart.service;

import com.gmart.dto.AuthResponse;
import com.gmart.dto.LoginRequest;
import com.gmart.dto.RegisterRequest;
import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import com.gmart.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    public AuthResponse login(LoginRequest request) {
        String usernameOrId = request.getUsername().trim();
        String password = request.getPassword().trim();

        
        // 1. Check if it's the Admin login (1111 / 12345678 or username "admin")
        if (("1111".equals(usernameOrId) || "admin".equalsIgnoreCase(usernameOrId)) && "12345678".equals(password)) {
            // Ensure admin entity exists in DB
            User admin = userRepository.findByUsername("admin").orElseGet(() -> {
                User newAdmin = new User();
                newAdmin.setUsername("admin");
                newAdmin.setFullName("System Administrator");
                newAdmin.setEmail("admin@gmart.com");
                newAdmin.setMobileNo(9999999999L);
                newAdmin.setPassWord(passwordEncoder.encode("12345678"));
                newAdmin.setAddress("GMart HQ, Admin Tower");
                newAdmin.setRole("ROLE_ADMIN");
                return userRepository.save(newAdmin);
            });

            String token = jwtTokenProvider.generateToken(admin.getUsername(), "ROLE_ADMIN");
            return new AuthResponse(token, admin.getCustId(), admin.getUsername(), admin.getFullName(), admin.getEmail(), "ROLE_ADMIN", admin.getAddress());
        }

        // 2. Customer login
        Optional<User> userOpt = Optional.empty();
        try {
            Long custId = Long.parseLong(usernameOrId);
            userOpt = userRepository.findById(custId);
        } catch (NumberFormatException ignored) {
        }

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(usernameOrId);
        }
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(usernameOrId);
        }

        if (userOpt.isEmpty()) {
            throw new RuntimeException("Invalid Login ID or Password");
        }

        User user = userOpt.get();

        // Support both hashed BCrypt and legacy plain text matches
        boolean passwordMatches = passwordEncoder.matches(password, user.getPassWord()) || password.equals(user.getPassWord());

        if (!passwordMatches) {
            throw new RuntimeException("Invalid Login ID or Password");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getCustId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(), user.getAddress());
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail());
        user.setMobileNo(request.getMobileNo());
        user.setPassWord(passwordEncoder.encode(request.getPassWord()));
        user.setAddress(request.getAddress());
        user.setRole("ROLE_CUSTOMER");

        return userRepository.save(user);
    }

    public User getCurrentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
