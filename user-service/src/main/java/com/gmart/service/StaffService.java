package com.gmart.service;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import com.gmart.web.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Admin accounts, managed by existing admins (Admin -> Staff). Customers can never
 * make themselves admin: sign-up always creates ROLE_CUSTOMER.
 */
@Service
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String CUSTOMER = "ROLE_CUSTOMER";
    public static final int ADMIN_PASSWORD_MIN = 12;

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public StaffService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public List<User> listAdmins() {
        return users.findByRoleOrderByUsernameAsc(ADMIN);
    }

    /** Find an existing account by username or email (to promote it). */
    public User find(String usernameOrEmail) {
        String q = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        return users.findByUsername(q)
                .or(() -> users.findByEmail(q.toLowerCase(Locale.ROOT)))
                .or(() -> users.findByEmail(q))
                .orElseThrow(() -> new BusinessException("No account with that username or email", 404));
    }

    @Transactional
    public User createAdmin(String actor, String username, String fullName, String email, String password) {
        if (isBlank(username) || isBlank(email) || isBlank(fullName)) {
            throw new BusinessException("Name, username and email are required");
        }
        if (password == null || password.length() < ADMIN_PASSWORD_MIN) {
            throw new BusinessException("Admin passwords must be at least " + ADMIN_PASSWORD_MIN + " characters");
        }
        if (users.existsByUsername(username.trim())) {
            throw new BusinessException("Username already exists");
        }
        if (users.existsByEmail(email.trim())) {
            throw new BusinessException("Email already registered");
        }
        User u = new User();
        u.setUsername(username.trim());
        u.setFullName(fullName.trim());
        u.setEmail(email.trim());
        u.setPassWord(encoder.encode(password));
        u.setRole(ADMIN);
        User saved = users.save(u);
        log.warn("AUDIT: admin '{}' created admin account '{}'", actor, saved.getUsername());
        return saved;
    }

    /**
     * Make an account admin or customer. An admin can't change their own role, and the
     * last admin can't be removed (so the shop is never left without one).
     */
    @Transactional
    public User setRole(String actor, String username, String role) {
        if (!ADMIN.equals(role) && !CUSTOMER.equals(role)) {
            throw new BusinessException("Role must be ROLE_ADMIN or ROLE_CUSTOMER");
        }
        User u = users.findByUsername(username)
                .orElseThrow(() -> new BusinessException("Account not found", 404));
        if (u.getUsername().equals(actor)) {
            throw new BusinessException("You can't change your own role. Ask another admin.");
        }
        if (role.equals(u.getRole())) {
            return u;
        }
        if (CUSTOMER.equals(role) && users.countByRole(ADMIN) <= 1) {
            throw new BusinessException("This is the only admin. Add another admin first.");
        }
        u.setRole(role);
        User saved = users.save(u);
        log.warn("AUDIT: admin '{}' changed role of '{}' to {}", actor, username, role);
        return saved;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
