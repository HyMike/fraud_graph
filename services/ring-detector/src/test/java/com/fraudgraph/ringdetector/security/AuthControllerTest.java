package com.fraudgraph.ringdetector.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real login against the seeded users in Postgres, and the issued token used as a
 * Bearer header — the end-to-end path the dashboard takes.
 *
 * Requires Neo4j and Postgres running (docker compose -f infra/docker-compose.yml up -d neo4j postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "scheduling.enabled=false")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${auth.seed-user-password}")
    private String seedPassword;

    @Test
    void seededUserLogsInAndTheTokenGrantsTheirRole() throws Exception {
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("auditor", seedPassword)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());

        assertThat(login.get("username").asText()).isEqualTo("auditor");
        assertThat(login.get("role").asText()).isEqualTo("AUDITOR");
        String bearer = "Bearer " + login.get("token").asText();

        mockMvc.perform(get("/api/decisions").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/clusters/recompute").header("Authorization", bearer))
            .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("analyst", seedPassword + "-wrong")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserIsRejectedTheSameWay() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("nobody", seedPassword)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clusters").header("Authorization", "Bearer not.a.jwt"))
            .andExpect(status().isUnauthorized());
    }

    private String credentials(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequest(username, password));
    }
}
