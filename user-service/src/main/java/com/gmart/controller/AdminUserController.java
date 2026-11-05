package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.User;
import com.gmart.service.StaffService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin -> Staff: list admins, add an admin, promote or demote an account. Admins only. */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminUserController {

    public record NewAdmin(String username, String fullName, String email, String password) {
    }

    public record RoleChange(String role) {
    }

    private final StaffService staff;

    public AdminUserController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping("/admins")
    public ResponseEntity<ApiResponse<List<User>>> admins() {
        return ResponseEntity.ok(ApiResponse.success("Admins", staff.listAdmins()));
    }

    /** Look up an existing account by username or email (before promoting it). */
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<User>> lookup(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success("Account", staff.find(q)));
    }

    @PostMapping("/admins")
    public ResponseEntity<ApiResponse<User>> create(Authentication auth, @RequestBody NewAdmin body) {
        return ResponseEntity.ok(ApiResponse.success("Admin account created",
                staff.createAdmin(auth.getName(), body.username(), body.fullName(), body.email(), body.password())));
    }

    @PutMapping("/{username}/role")
    public ResponseEntity<ApiResponse<User>> setRole(Authentication auth, @PathVariable String username, @RequestBody RoleChange body) {
        User u = staff.setRole(auth.getName(), username, body.role());
        String msg = StaffService.ADMIN.equals(u.getRole()) ? u.getUsername() + " is now an admin"
                : u.getUsername() + " is no longer an admin";
        return ResponseEntity.ok(ApiResponse.success(msg, u));
    }
}
