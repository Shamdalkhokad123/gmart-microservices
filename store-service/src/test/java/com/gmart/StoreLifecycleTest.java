package com.gmart;

import com.gmart.client.PlatformClients;
import com.gmart.security.JwtTokenProvider;
import com.gmart.security.Roles;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apply -> approve / reject / suspend / reactivate, and who may do it (h2, other services mocked). */
@SpringBootTest(properties = "gmart.seed.demo-data=false")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class StoreLifecycleTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockBean PlatformClients platform;

    private String bearer(String user, String role, Integer storeId) {
        return "Bearer " + jwt.generateToken(user, role, storeId);
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String auth, String body) throws Exception {
        return mvc.perform(req.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : body));
    }

    private static String form(String name) {
        return "{\"name\":\"" + name + "\",\"phone\":\"9845012345\",\"address\":\"1 MG Road\",\"city\":\"Bengaluru\","
                + "\"pincode\":\"560001\",\"gstin\":\"29abcde1234f1z5\",\"fssai\":\"12345678901234\"}";
    }

    private int apply(String user) throws Exception {
        String body = call(post("/stores/apply"), bearer(user, Roles.CUSTOMER, null), form("Shop of " + user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.gstin").value("29ABCDE1234F1Z5"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private static String user() {
        return "u" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void approvedStoreBecomesVisibleAndTheApplicantItsOwner() throws Exception {
        String applicant = user();
        int id = apply(applicant);
        mvc.perform(get("/stores/" + id)).andExpect(status().isNotFound()); // pending: hidden

        String admin = bearer("root", Roles.SUPER_ADMIN, null);
        call(post("/stores/admin/" + id + "/approve"), admin, "{\"commissionPercent\":12.5,\"razorpayAccountId\":\"acc_ABCDEFGH12345678\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        verify(platform).makeOwner(applicant, id);
        verify(platform).syncCatalog(eq(id), anyString(), eq(true));

        mvc.perform(get("/stores/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.onlinePayments").value(true))
                // customers don't see commission, payout account or owner username
                .andExpect(jsonPath("$.data.commissionPercent").doesNotExist())
                .andExpect(jsonPath("$.data.razorpayAccountId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerUsername").doesNotExist());
    }

    @Test
    void onlyOneOpenApplicationPerPersonAndOnlyCustomersApply() throws Exception {
        String applicant = user();
        apply(applicant);
        call(post("/stores/apply"), bearer(applicant, Roles.CUSTOMER, null), form("Second"))
                .andExpect(status().isConflict());
        call(post("/stores/apply"), bearer(user(), Roles.STAFF, 1), form("Staff shop"))
                .andExpect(status().isConflict());
    }

    @Test
    void applicationIsValidated() throws Exception {
        String t = bearer(user(), Roles.CUSTOMER, null);
        call(post("/stores/apply"), t, form("X").replace("560001", "56"))
                .andExpect(jsonPath("$.message").value("PIN code must be 6 digits"));
        call(post("/stores/apply"), t, form("X").replace("29abcde1234f1z5", "BADGSTIN"))
                .andExpect(jsonPath("$.message").value("GSTIN doesn't look right (15 characters, e.g. 29ABCDE1234F1Z5)"));
    }

    @Test
    void onlySuperAdminReviews() throws Exception {
        int id = apply(user());
        call(post("/stores/admin/" + id + "/approve"), bearer("owner", Roles.STORE_OWNER, 1), null).andExpect(status().isForbidden());
        call(post("/stores/admin/" + id + "/approve"), bearer("cust", Roles.CUSTOMER, null), null).andExpect(status().isForbidden());
        mvc.perform(get("/stores/admin").header("Authorization", bearer("staff", Roles.STAFF, 1))).andExpect(status().isForbidden());
    }

    @Test
    void rejectNeedsAReasonAndTheApplicantSeesIt() throws Exception {
        String applicant = user();
        int id = apply(applicant);
        String admin = bearer("root", Roles.SUPER_ADMIN, null);
        call(post("/stores/admin/" + id + "/reject"), admin, "{\"note\":\"\"}").andExpect(status().isBadRequest());
        call(post("/stores/admin/" + id + "/reject"), admin, "{\"note\":\"FSSAI licence missing\"}").andExpect(status().isOk());
        mvc.perform(get("/stores/my-applications").header("Authorization", bearer(applicant, Roles.CUSTOMER, null)))
                .andExpect(jsonPath("$.data[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].reviewNote").value("FSSAI licence missing"));
        verify(platform, never()).makeOwner(eq(applicant), any());
    }

    @Test
    void suspendHidesAndReactivateShows() throws Exception {
        int id = apply(user());
        String admin = bearer("root", Roles.SUPER_ADMIN, null);
        call(post("/stores/admin/" + id + "/approve"), admin, null).andExpect(status().isOk());
        call(post("/stores/admin/" + id + "/suspend"), admin, "{\"note\":\"Complaints\"}").andExpect(status().isOk());
        verify(platform).syncCatalog(eq(id), anyString(), eq(false));
        mvc.perform(get("/stores/" + id)).andExpect(status().isNotFound());
        call(post("/stores/admin/" + id + "/reactivate"), admin, null).andExpect(status().isOk());
        mvc.perform(get("/stores/" + id)).andExpect(status().isOk());
    }

    @Test
    void ownerManagesOnlyTheirStoreSettings() throws Exception {
        int id = apply(user());
        call(post("/stores/admin/" + id + "/approve"), bearer("root", Roles.SUPER_ADMIN, null), null);
        String owner = bearer("owner", Roles.STORE_OWNER, id);
        call(put("/stores/mine"), owner, "{\"store\":" + form("Renamed shop") + ",\"open\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed shop"))
                .andExpect(jsonPath("$.data.open").value(false));
        // staff can see their store but not change it
        mvc.perform(get("/stores/mine").header("Authorization", bearer("staff", Roles.STAFF, id))).andExpect(status().isOk());
        call(put("/stores/mine"), bearer("staff", Roles.STAFF, id), "{\"store\":" + form("Nope") + "}").andExpect(status().isForbidden());
        // owner can't change commission or the payout account
        call(put("/stores/admin/" + id), owner, "{\"commissionPercent\":0}").andExpect(status().isForbidden());
    }
}
