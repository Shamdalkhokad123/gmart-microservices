package com.gmart;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import com.gmart.security.Roles;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Super admins, store owners managing their own staff, and change password (h2, no Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class TeamAndPasswordIntegrationTest {

    private static final String PW = "long-password-1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User account(String role, Integer storeId) {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        User u = new User();
        u.setUsername(name);
        u.setEmail(name + "@example.com");
        u.setFullName(name);
        u.setPassWord(encoder.encode(PW));
        u.setRole(role);
        u.setStoreId(storeId);
        return users.save(u);
    }

    private String token(User u) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "192.0.2." + (u.getUsername().hashCode() & 0x7f))
                        .content("{\"username\":\"" + u.getUsername() + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.data.token");
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String auth, String body) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body));
    }

    private static String newAccountJson(String name, String password) {
        return "{\"username\":\"" + name + "\",\"fullName\":\"N\",\"email\":\"" + name + "@x.com\",\"password\":\"" + password + "\"}";
    }

    @Test
    void loginReturnsRoleAndStore() throws Exception {
        User owner = account(Roles.STORE_OWNER, 7);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).header("X-Forwarded-For", "192.0.2.250")
                        .content("{\"username\":\"" + owner.getUsername() + "\",\"password\":\"" + PW + "\"}"))
                .andExpect(jsonPath("$.data.role").value("ROLE_STORE_OWNER"))
                .andExpect(jsonPath("$.data.storeId").value(7));
    }

    @Test
    void onlySuperAdminsManageSuperAdmins() throws Exception {
        String owner = token(account(Roles.STORE_OWNER, 1));
        String customer = token(account(Roles.CUSTOMER, null));
        mvc.perform(get("/users/super-admins").header("Authorization", owner)).andExpect(status().isForbidden());
        mvc.perform(get("/users/super-admins").header("Authorization", customer)).andExpect(status().isForbidden());

        String sa = token(account(Roles.SUPER_ADMIN, null));
        String name = "sa" + UUID.randomUUID().toString().substring(0, 6);
        call(post("/users/super-admins"), sa, newAccountJson(name, "short")).andExpect(status().isBadRequest());
        call(post("/users/super-admins"), sa, newAccountJson(name, PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.passWord").doesNotExist());
    }

    @Test
    void superAdminRoleRules() throws Exception {
        User actor = account(Roles.SUPER_ADMIN, null);
        String t = token(actor);
        User customer = account(Roles.CUSTOMER, null);
        User owner = account(Roles.STORE_OWNER, 3);

        call(put("/users/super-admins/" + customer.getUsername()), t, "{\"superAdmin\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ROLE_SUPER_ADMIN"));
        call(put("/users/super-admins/" + owner.getUsername()), t, "{\"superAdmin\":true}")
                .andExpect(status().isBadRequest());
        call(put("/users/super-admins/" + actor.getUsername()), t, "{\"superAdmin\":false}")
                .andExpect(jsonPath("$.message").value("You can't change your own role. Ask another super admin."));
        call(put("/users/super-admins/" + customer.getUsername()), t, "{\"superAdmin\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("ROLE_CUSTOMER"));
    }

    @Test
    void ownerManagesOnlyTheirOwnStaff() throws Exception {
        User ownerA = account(Roles.STORE_OWNER, 101);
        User staffB = account(Roles.STAFF, 202);
        String a = token(ownerA);
        String name = "st" + UUID.randomUUID().toString().substring(0, 6);

        call(post("/users/store-staff"), a, newAccountJson(name, PW))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_STAFF"))
                .andExpect(jsonPath("$.data.storeId").value(101));
        mvc.perform(get("/users/store-staff").header("Authorization", a))
                .andExpect(jsonPath("$.data[?(@.username=='" + name + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.username=='" + staffB.getUsername() + "')]").doesNotExist());

        // another store's staff can't be removed
        call(delete("/users/store-staff/" + staffB.getUsername()), a, null).andExpect(status().isForbidden());
        call(delete("/users/store-staff/" + name), a, null).andExpect(status().isOk());
        assertThat(users.findByUsername(name).orElseThrow().getRole()).isEqualTo(Roles.CUSTOMER);
    }

    @Test
    void staffAndCustomersCannotManageStaff() throws Exception {
        String staff = token(account(Roles.STAFF, 101));
        mvc.perform(get("/users/store-staff").header("Authorization", staff)).andExpect(status().isForbidden());
        call(post("/users/store-staff"), staff, newAccountJson("x" + UUID.randomUUID().toString().substring(0, 6), PW))
                .andExpect(status().isForbidden());
    }

    @Test
    void passwordChangeNeedsTheCurrentPasswordAndLengthByRole() throws Exception {
        User c = account(Roles.CUSTOMER, null);
        String t = token(c);
        call(post("/auth/change-password"), t, "{\"currentPassword\":\"wrong\",\"newPassword\":\"new-password-1\"}")
                .andExpect(jsonPath("$.message").value("Your current password is not correct"));
        call(post("/auth/change-password"), t, "{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"short\"}")
                .andExpect(jsonPath("$.message").value("The new password must be at least 8 characters"));
        call(post("/auth/change-password"), t, "{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"new-password-1\"}")
                .andExpect(status().isOk());

        String staff = token(account(Roles.STAFF, 1));
        call(post("/auth/change-password"), staff, "{\"currentPassword\":\"" + PW + "\",\"newPassword\":\"only-11-chr\"}")
                .andExpect(jsonPath("$.message").value("The new password must be at least 12 characters"));
    }
}
