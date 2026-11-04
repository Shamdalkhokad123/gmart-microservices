package com.gmart.config;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin account in a fresh production database from environment
 * variables (ADMIN_USERNAME, ADMIN_EMAIL, ADMIN_PASSWORD). It does nothing once any
 * admin exists, so the variables can be removed after the first start.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         @Value("${gmart.bootstrap.admin.username:}") String username,
                         @Value("${gmart.bootstrap.admin.email:}") String email,
                         @Value("${gmart.bootstrap.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username.trim();
        this.email = email.trim();
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole("ROLE_ADMIN")) {
            return;
        }
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            log.warn("No admin account exists. Set ADMIN_USERNAME, ADMIN_EMAIL and ADMIN_PASSWORD and restart to create one.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new IllegalStateException("Cannot create admin: username or email already belongs to a customer account");
        }
        User admin = new User();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setFullName("Administrator");
        admin.setPassWord(passwordEncoder.encode(password));
        admin.setRole("ROLE_ADMIN");
        userRepository.save(admin);
        log.info("Created admin account '{}'. You can now remove ADMIN_PASSWORD from the environment.", username);
    }
}
