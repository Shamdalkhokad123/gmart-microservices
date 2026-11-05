package com.gmart;

import com.gmart.entity.User;
import com.gmart.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, password handling and lockout, end to end through the HTTP layer (h2, no Docker). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private User user(String username, String storedPassword) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setFullName(username);
        u.setPassWord(storedPassword);
        u.setRole("ROLE_CUSTOMER");
        return users.save(u);
    }

    @Test
    void oldHardCodedAdminLoginNoLongerWorks() throws Exception {
        login("1111", "12345678", "198.51.100.1").andExpect(status().isBadRequest());
    }

    @Test
    void passwordsNeverAppearInResponses() throws Exception {
        String token = com.jayway.jsonpath.JsonPath.read(
                login("john_doe", "password123", "198.51.100.2").andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(), "$.data.token");
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("john_doe"))
                .andExpect(jsonPath("$.data.passWord").doesNotExist());
    }

    @Test
    void storedHashCannotBeUsedAsThePassword() throws Exception {
        User u = user("hashuser", encoder.encode("real-password"));
        login("hashuser", u.getPassWord(), "198.51.100.3").andExpect(status().isBadRequest());
        login("hashuser", "real-password", "198.51.100.3").andExpect(status().isOk());
    }

    @Test
    void legacyPlainTextPasswordIsUpgradedOnLogin() throws Exception {
        user("legacy", "old-plain-pass");
        login("legacy", "old-plain-pass", "198.51.100.4").andExpect(status().isOk());
        String stored = users.findByUsername("legacy").orElseThrow().getPassWord();
        assertThat(stored).startsWith("$2").isNotEqualTo("old-plain-pass");
        login("legacy", "old-plain-pass", "198.51.100.4").andExpect(status().isOk());
    }

    @Test
    void accountLocksAfterFiveFailures() throws Exception {
        user("victim", encoder.encode("right-password"));
        for (int i = 0; i < 5; i++) {
            login("victim", "guess" + i, "198.51.100." + (10 + i)).andExpect(status().isBadRequest());
        }
        // even the right password is refused for now, from any IP
        login("victim", "right-password", "203.0.113.9").andExpect(status().isTooManyRequests());
    }
}
