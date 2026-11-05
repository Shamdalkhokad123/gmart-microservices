package com.gmart.service;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import com.gmart.security.Roles;
import com.gmart.web.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Back-office accounts:
 * - super admins (platform), managed by super admins;
 * - store staff, managed by the owner of that store;
 * - store owners, set by store-service when a store application is approved.
 * Nobody can raise their own access, and customers can't sign up as anything but customers.
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public TeamService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    // ---------------- super admins ----------------

    public List<User> superAdmins() {
        return users.findByRoleOrderByUsernameAsc(Roles.SUPER_ADMIN);
    }

    /** Find an existing account by username or email. */
    public User find(String usernameOrEmail) {
        String q = usernameOrEmail == null ? "" : usernameOrEmail.trim();
        return users.findByUsername(q)
                .or(() -> users.findByEmail(q.toLowerCase(Locale.ROOT)))
                .or(() -> users.findByEmail(q))
                .orElseThrow(() -> new BusinessException("No account with that username or email", 404));
    }

    @Transactional
    public User createSuperAdmin(String actor, String username, String fullName, String email, String password) {
        User saved = users.save(newAccount(username, fullName, email, password, Roles.SUPER_ADMIN, null));
        log.warn("AUDIT: '{}' created super admin '{}'", actor, saved.getUsername());
        return saved;
    }

    /**
     * Give or remove super admin rights. Only customers can be promoted (an owner or staff member
     * keeps their store role); you can't change your own role; the last super admin stays.
     */
    @Transactional
    public User setSuperAdmin(String actor, String username, boolean superAdmin) {
        User u = users.findByUsername(username).orElseThrow(() -> new BusinessException("Account not found", 404));
        if (u.getUsername().equals(actor)) {
            throw new BusinessException("You can't change your own role. Ask another super admin.");
        }
        if (superAdmin) {
            if (Roles.SUPER_ADMIN.equals(u.getRole())) return u;
            if (!Roles.CUSTOMER.equals(u.getRole())) {
                throw new BusinessException("This account belongs to a store. Only customer accounts can be made super admin.");
            }
            u.setRole(Roles.SUPER_ADMIN);
        } else {
            if (!Roles.SUPER_ADMIN.equals(u.getRole())) return u;
            if (users.countByRole(Roles.SUPER_ADMIN) <= 1) {
                throw new BusinessException("This is the only super admin. Add another one first.");
            }
            u.setRole(Roles.CUSTOMER);
        }
        u.setStoreId(null);
        User saved = users.save(u);
        log.warn("AUDIT: '{}' set role of '{}' to {}", actor, username, saved.getRole());
        return saved;
    }

    // ---------------- store staff ----------------

    public List<User> storeTeam(Integer storeId) {
        return users.findByStoreIdOrderByRoleDescUsernameAsc(storeId);
    }

    /** Owner adds a staff member to their store (a new account with a temporary password). */
    @Transactional
    public User createStaff(String owner, Integer storeId, String username, String fullName, String email, String password) {
        User saved = users.save(newAccount(username, fullName, email, password, Roles.STAFF, storeId));
        log.warn("AUDIT: owner '{}' added staff '{}' to store {}", owner, saved.getUsername(), storeId);
        return saved;
    }

    /** Owner removes a staff member: the account stays, as a customer. */
    @Transactional
    public User removeStaff(String owner, Integer storeId, String username) {
        User u = users.findByUsername(username).orElseThrow(() -> new BusinessException("Account not found", 404));
        if (!Roles.STAFF.equals(u.getRole()) || !storeId.equals(u.getStoreId())) {
            throw new BusinessException("That person is not staff of your store", 403);
        }
        u.setRole(Roles.CUSTOMER);
        u.setStoreId(null);
        User saved = users.save(u);
        log.warn("AUDIT: owner '{}' removed staff '{}' from store {}", owner, username, storeId);
        return saved;
    }

    // ---------------- store owners (store-service) ----------------

    /** Called by store-service when a store is approved: the applicant becomes its owner. */
    @Transactional
    public User makeOwner(String username, Integer storeId) {
        User u = users.findByUsername(username).orElseThrow(() -> new BusinessException("Account not found", 404));
        if (Roles.SUPER_ADMIN.equals(u.getRole())) {
            throw new BusinessException("A super admin can't also own a store", 409);
        }
        if (u.getStoreId() != null && !u.getStoreId().equals(storeId)) {
            throw new BusinessException("This account already belongs to another store", 409);
        }
        u.setRole(Roles.STORE_OWNER);
        u.setStoreId(storeId);
        User saved = users.save(u);
        log.warn("AUDIT: '{}' is now owner of store {}", username, storeId);
        return saved;
    }

    private User newAccount(String username, String fullName, String email, String password, String role, Integer storeId) {
        if (isBlank(username) || isBlank(email) || isBlank(fullName)) {
            throw new BusinessException("Name, username and email are required");
        }
        if (password == null || password.length() < AuthService.ELEVATED_PASSWORD_MIN) {
            throw new BusinessException("Passwords for store and admin accounts must be at least "
                    + AuthService.ELEVATED_PASSWORD_MIN + " characters");
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
        u.setRole(role);
        u.setStoreId(storeId);
        return u;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
