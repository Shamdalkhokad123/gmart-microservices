package com.gmart.config;

import com.gmart.entity.Address;
import com.gmart.entity.Employee;
import com.gmart.entity.User;
import com.gmart.repository.AddressRepository;
import com.gmart.repository.EmployeeRepository;
import com.gmart.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/** Seeds demo users, employees and addresses on first start. */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, EmployeeRepository employeeRepository,
                           AddressRepository addressRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedEmployees();
        seedAddresses();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            // Admin user (matches legacy login 1111 / 12345678)
            User admin = new User();
            admin.setUsername("admin");
            admin.setFullName("Super Administrator");
            admin.setEmail("admin@gmart.com");
            admin.setMobileNo(9876543210L);
            admin.setPassWord(passwordEncoder.encode("12345678"));
            admin.setAddress("GMart Corporate Tower, 5th Floor, Silicon Valley");
            admin.setRole("ROLE_ADMIN");
            userRepository.save(admin);

            // Customer user
            User customer = new User();
            customer.setUsername("john_doe");
            customer.setFullName("John Doe");
            customer.setEmail("john.doe@example.com");
            customer.setMobileNo(9123456780L);
            customer.setPassWord(passwordEncoder.encode("password123"));
            customer.setAddress("742 Evergreen Terrace, Springfield, OR 97477");
            customer.setRole("ROLE_CUSTOMER");
            userRepository.save(customer);

            System.out.println("Default Admin: username 'admin' (or '1111'), password '12345678'");
            System.out.println("Default Customer: username 'john_doe', password 'password123'");
        }
    }

    private void seedEmployees() {
        if (employeeRepository.count() == 0) {
            List<Employee> employees = Arrays.asList(
                    new Employee(null, "Robert", "Langdon", 9820011223L, "robert.l@gmart.com", "empPass1", "Floor 2, Inventory Hub"),
                    new Employee(null, "Sarah", "Connor", 9820022334L, "sarah.c@gmart.com", "empPass2", "Customer Relations Desk"),
                    new Employee(null, "David", "Miller", 9820033445L, "david.m@gmart.com", "empPass3", "Logistics & Dispatch Division")
            );
            employeeRepository.saveAll(employees);
        }
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
