package com.gffh.api.web;

import com.gffh.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/admin/** is restricted to ROLE_PLATFORM_ADMIN in SecurityConfig.
 * A regular user token deliberately carries no role claim (see
 * SecurityConfig's javadoc), so this is the one authorization boundary every
 * admin endpoint depends on getting right - worth pinning down directly
 * rather than trusting each of the nine admin controllers individually.
 */
class AdminAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("an ordinary user token is rejected by an admin endpoint")
    void ordinaryUserCannotReachAdminEndpoint() throws Exception {
        TestAccount account = registerAccount("Not An Admin");

        mockMvc.perform(get("/api/v1/admin/verifications")
                        .header("Authorization", "Bearer " + account.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an unauthenticated request is rejected by an admin endpoint")
    void unauthenticatedRequestCannotReachAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/verifications"))
                .andExpect(status().isUnauthorized());
    }
}
