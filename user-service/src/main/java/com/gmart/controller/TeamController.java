package com.gmart.controller;

import com.gmart.dto.ApiResponse;
import com.gmart.entity.User;
import com.gmart.security.CurrentUser;
import com.gmart.service.TeamService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Back-office accounts.
 * /users/super-admins: platform admins (super admins only).
 * /users/store-staff: the signed-in owner's own store team (store owners only).
 */
@RestController
@RequestMapping("/users")
public class TeamController {

    public record NewAccount(String username, String fullName, String email, String password) {
    }

    public record RoleChange(boolean superAdmin) {
    }

    private final TeamService team;

    public TeamController(TeamService team) {
        this.team = team;
    }

    // ---- super admins ----

    @GetMapping("/super-admins")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<User>>> superAdmins() {
        return ResponseEntity.ok(ApiResponse.success("Super admins", team.superAdmins()));
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> lookup(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success("Account", team.find(q)));
    }

    @PostMapping("/super-admins")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> createSuperAdmin(Authentication auth, @RequestBody NewAccount body) {
        return ResponseEntity.ok(ApiResponse.success("Super admin created",
                team.createSuperAdmin(auth.getName(), body.username(), body.fullName(), body.email(), body.password())));
    }

    @PutMapping("/super-admins/{username}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<User>> setSuperAdmin(Authentication auth, @PathVariable String username, @RequestBody RoleChange body) {
        User u = team.setSuperAdmin(auth.getName(), username, body.superAdmin());
        return ResponseEntity.ok(ApiResponse.success(body.superAdmin()
                ? u.getUsername() + " is now a super admin" : u.getUsername() + " is no longer a super admin", u));
    }

    // ---- store staff (owner of the signed-in store) ----

    @GetMapping("/store-staff")
    @PreAuthorize("hasAuthority('ROLE_STORE_OWNER')")
    public ResponseEntity<ApiResponse<List<User>>> storeTeam(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Team", team.storeTeam(CurrentUser.of(auth).requireStore())));
    }

    @PostMapping("/store-staff")
    @PreAuthorize("hasAuthority('ROLE_STORE_OWNER')")
    public ResponseEntity<ApiResponse<User>> addStaff(Authentication auth, @RequestBody NewAccount body) {
        CurrentUser me = CurrentUser.of(auth);
        return ResponseEntity.ok(ApiResponse.success("Staff member added",
                team.createStaff(me.username(), me.requireStore(), body.username(), body.fullName(), body.email(), body.password())));
    }

    @DeleteMapping("/store-staff/{username}")
    @PreAuthorize("hasAuthority('ROLE_STORE_OWNER')")
    public ResponseEntity<ApiResponse<User>> removeStaff(Authentication auth, @PathVariable String username) {
        CurrentUser me = CurrentUser.of(auth);
        return ResponseEntity.ok(ApiResponse.success(username + " removed from your store",
                team.removeStaff(me.username(), me.requireStore(), username)));
    }
}
