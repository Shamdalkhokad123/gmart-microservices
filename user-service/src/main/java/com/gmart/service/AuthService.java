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

        // Admins and customers sign in the same way: the account and its stored password decide the role.
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

        if (!passwordMatches(user, password)) {
            throw new RuntimeException("Invalid Login ID or Password");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getCustId(), user.getUsername(), user.getFullName(), user.getEmail(), user.getRole(), user.getAddress());
    }

    /**
     * BCrypt check. Accounts imported from the old JSP app may still hold a plain-text
     * password: those (and only those) are compared directly once and upgraded to BCrypt,
     * so a leaked hash can never be used as a password.
     */
    private boolean passwordMatches(User user, String password) {
        String stored = user.getPassWord();
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        if (BCRYPT.matcher(stored).matches()) {
            return passwordEncoder.matches(password, stored);
        }
        boolean legacyMatch = java.security.MessageDigest.isEqual(
                stored.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (legacyMatch) {
            user.setPassWord(passwordEncoder.encode(password));
            userRepository.save(user);
        }
        return legacyMatch;
    }

    private static final java.util.regex.Pattern BCRYPT = java.util.regex.Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

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
