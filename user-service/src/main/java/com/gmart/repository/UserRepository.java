package com.gmart.repository;

import com.gmart.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(String role);

    long countByRole(String role);

    java.util.List<User> findByRoleOrderByUsernameAsc(String role);

    /** Owner first (ROLE_STORE_OWNER sorts after ROLE_STAFF), then staff. */
    java.util.List<User> findByStoreIdOrderByRoleDescUsernameAsc(Integer storeId);
}
