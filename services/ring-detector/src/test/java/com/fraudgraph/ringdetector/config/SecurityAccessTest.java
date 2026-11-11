package com.fraudgraph.ringdetector.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role matrix in SecurityConfig. Only checks that are rejected before reaching
 * a controller, or that are cheap reads, are exercised here — a permitted
 * recompute would renumber every community under the other test classes.
 *
 * Requires Neo4j and Postgres running (docker compose -f infra/docker-compose.yml up -d neo4j postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "scheduling.enabled=false")
class SecurityAccessTest {

    private static final String DECISION_BODY =
        "{\"decision\": \"CONFIRM\", \"reason\": \"r\", \"memberFingerprint\": \"x\"}";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(token -> token.subject(role.toLowerCase()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void everyApiCallWithoutATokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/clusters")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/detection/cycles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/decisions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clusters/recompute")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/clusters/1/decision")
                .contentType(MediaType.APPLICATION_JSON).content(DECISION_BODY))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYST", "LEAD", "AUDITOR", "ADMIN"})
    void everyRoleCanViewClusters(String role) throws Exception {
        mockMvc.perform(get("/api/clusters").with(as(role))).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ANALYST", "AUDITOR"})
    void onlyLeadsAndAdminsCanRecompute(String role) throws Exception {
        mockMvc.perform(post("/api/clusters/recompute").with(as(role))).andExpect(status().isForbidden());
    }

    @Test
    void auditorsCannotRecordDecisions() throws Exception {
        mockMvc.perform(post("/api/clusters/1/decision").with(as("AUDITOR"))
                .contentType(MediaType.APPLICATION_JSON).content(DECISION_BODY))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEAD", "ADMIN"})
    void leadsAndAdminsCanRecordDecisions(String role) throws Exception {
        // Not 403: the request reaches DecisionService, which rejects the made-up
        // fingerprint for a community that does not exist (409), writing nothing.
        mockMvc.perform(post("/api/clusters/-1/decision").with(as(role))
                .contentType(MediaType.APPLICATION_JSON).content(DECISION_BODY))
            .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUDITOR", "LEAD", "ADMIN"})
    void auditorsLeadsAndAdminsCanReadTheAuditLog(String role) throws Exception {
        mockMvc.perform(get("/api/decisions").with(as(role))).andExpect(status().isOk());
    }

    @Test
    void analystsCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/api/decisions").with(as("ANALYST"))).andExpect(status().isForbidden());
    }

    @Test
    void aTokenWithNoKnownRoleIsForbidden() throws Exception {
        mockMvc.perform(get("/api/clusters").with(jwt())).andExpect(status().isForbidden());
    }
}
