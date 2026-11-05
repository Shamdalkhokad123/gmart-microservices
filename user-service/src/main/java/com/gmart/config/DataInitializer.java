package com.gmart.config;

import com.gmart.entity.Address;
import com.gmart.entity.User;
import com.gmart.repository.AddressRepository;
import com.gmart.repository.UserRepository;
import com.gmart.security.Roles;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demo accounts for local development (gmart.seed.demo-data=true, the dev profile).
 * Store ids 1 and 2 match the demo stores seeded by store-service.
 *
 *   admin / 12345678            super admin (platform)
 *   store_owner / password123   owner of store 1 (GMart Indiranagar)
 *   store_staff / password123   staff of store 1
 *   fresh_owner / password123   owner of store 2 (Fresh Basket Koramangala)
 *   john_doe / password123      customer
 */
@Component
@ConditionalOnProperty(name = "gmart.seed.demo-data", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, AddressRepository addressRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedAddresses();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        user("admin", "Super Administrator", "admin@gmart.com", 9876543210L, "12345678", Roles.SUPER_ADMIN, null);
        user("store_owner", "Ravi Sharma", "ravi@gmart-indiranagar.example", 9845000001L, "password123", Roles.STORE_OWNER, 1);
        user("store_staff", "Meena Rao", "meena@gmart-indiranagar.example", 9845000002L, "password123", Roles.STAFF, 1);
        user("fresh_owner", "Anita Kulkarni", "anita@freshbasket.example", 9845000003L, "password123", Roles.STORE_OWNER, 2);
        user("john_doe", "John Doe", "john.doe@example.com", 9123456780L, "password123", Roles.CUSTOMER, null);
        System.out.println("Demo logins: admin/12345678 (super admin), store_owner, store_staff, fresh_owner, john_doe (password123)");
    }

    private void user(String username, String fullName, String email, long mobile, String password, String role, Integer storeId) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setMobileNo(mobile);
        u.setPassWord(passwordEncoder.encode(password));
        u.setRole(role);
        u.setStoreId(storeId);
        userRepository.save(u);
    }

    private void seedAddresses() {
        if (addressRepository.count() == 0 && userRepository.findByUsername("john_doe").isPresent()) {
            Address home = new Address();
            home.setUsername("john_doe");
            home.setLabel("Home");
            home.setReceiverName("John Doe");
            home.setPhone("9123456780");
            home.setLine1("Flat 302, Sunrise Apartments, 12th Main Road, Indiranagar");
            home.setLandmark("CMH Hospital");
            home.setCity("Bengaluru");
            home.setPincode("560038");
            home.setDefaultAddress(true);

            Address work = new Address();
            work.setUsername("john_doe");
            work.setLabel("Work");
            work.setReceiverName("John Doe");
            work.setPhone("9123456780");
            work.setLine1("4th Floor, Tech Park Tower B, Outer Ring Road, Bellandur");
            work.setLandmark("Ecospace Signal");
            work.setCity("Bengaluru");
            work.setPincode("560103");
            addressRepository.saveAll(List.of(home, work));
        }
    }
}
