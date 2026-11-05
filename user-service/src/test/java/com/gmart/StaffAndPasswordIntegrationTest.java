package com.gmart;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin -> Staff and change password, through the HTTP layer (h2, no Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class StaffAndPasswordIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private String unique() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    private User account(String role, String password) {
        String name = unique();
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@example.com");
        u.setFullName(name);
        u.setPassWord(encoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    private String token(String username, String password) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "192.0.2." + (username.hashCode() & 0x7f))
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.token");
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String auth, String body) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void customersCannotUseTheStaffApi() throws Exception {
        User c = account("ROLE_CUSTOMER", "customer-pass");
        String t = token(c.getUsername(), "customer-pass");
        mvc.perform(get("/users/admins").header("Authorization", t)).andExpect(status().isForbidden());
        json(put("/users/" + c.getUsername() + "/role"), t, "{\"role\":\"ROLE_ADMIN\"}").andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesAnotherAdminWithAStrongPassword() throws Exception {
        User a = account("ROLE_ADMIN", "admin-password-1");
        String t = token(a.getUsername(), "admin-password-1");
        String name = unique();

        json(post("/users/admins"), t, "{\"username\":\"" + name + "\",\"fullName\":\"New\",\"email\":\"" + name + "@x.com\",\"password\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Admin passwords must be at least 12 characters"));

        json(post("/users/admins"), t, "{\"username\":\"" + name + "\",\"fullName\":\"New\",\"email\":\"" + name + "@x.com\",\"password\":\"long-enough-pass\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.data.passWord").doesNotExist());
        token(name, "long-enough-pass"); // can log in
    }

    @Test
    void promoteAndDemoteWithSafetyRules() throws Exception {
        User a = account("ROLE_ADMIN", "admin-password-1");
        User c = account("ROLE_CUSTOMER", "customer-pass");
        String t = token(a.getUsername(), "admin-password-1");

        json(put("/users/" + c.getUsername() + "/role"), t, "{\"role\":\"ROLE_ADMIN\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));
        json(put("/users/" + a.getUsername() + "/role"), t, "{\"role\":\"ROLE_CUSTOMER\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't change your own role. Ask another admin."));
        json(put("/users/" + c.getUsername() + "/role"), t, "{\"role\":\"ROLE_CUSTOMER\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"));
        json(put("/users/" + c.getUsername() + "/role"), t, "{\"role\":\"ROLE_GOD\"}").andExpect(status().isBadRequest());
    }

    /**
     * Because admins can't demote themselves, the acting admin always remains, so the shop
     * can't be left without an admin (StaffService also refuses to remove the last one).
     */
    @Test
    void demotingOthersAlwaysLeavesAnAdmin() throws Exception {
        // demote every other admin so exactly two remain: the actor and one target
        User actor = account("ROLE_ADMIN", "admin-password-1");
        users.findByRoleOrderByUsernameAsc("ROLE_ADMIN").stream()
                .filter(u -> !u.getUsername().equals(actor.getUsername()))
                .forEach(u -> { u.setRole("ROLE_CUSTOMER"); users.save(u); });
        User other = account("ROLE_ADMIN", "admin-password-2");
        String t = token(actor.getUsername(), "admin-password-1");

        json(put("/users/" + other.getUsername() + "/role"), t, "{\"role\":\"ROLE_CUSTOMER\"}").andExpect(status().isOk());
        assertThat(users.countByRole("ROLE_ADMIN")).isEqualTo(1);
        // the remaining admin is the actor, who can't demote themselves either way
    }

    @Test
    void passwordChangeNeedsTheCurrentPassword() throws Exception {
        User c = account("ROLE_CUSTOMER", "old-password");
        String t = token(c.getUsername(), "old-password");

        json(post("/auth/change-password"), t, "{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-1\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Your current password is not correct"));
        json(post("/auth/change-password"), t, "{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}")
                .andExpect(jsonPath("$.message").value("The new password must be at least 8 characters"));
        json(post("/auth/change-password"), t, "{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password-1\"}")
                .andExpect(status().isOk());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "192.0.2.200")
                .content("{\"username\":\"" + c.getUsername() + "\",\"password\":\"old-password\"}")).andExpect(status().isBadRequest());
        token(c.getUsername(), "new-password-1");
    }

    @Test
    void adminsNeedLongerPasswords() throws Exception {
        User a = account("ROLE_ADMIN", "admin-password-1");
        String t = token(a.getUsername(), "admin-password-1");
        json(post("/auth/change-password"), t, "{\"currentPassword\":\"admin-password-1\",\"newPassword\":\"only-11-chr\"}")
                .andExpect(jsonPath("$.message").value("The new password must be at least 12 characters"));
    }

    @Test
    void changePasswordRequiresLogin() throws Exception {
        mvc.perform(post("/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"a\",\"newPassword\":\"bbbbbbbbbb\"}")).andExpect(status().isForbidden());
    }
}
