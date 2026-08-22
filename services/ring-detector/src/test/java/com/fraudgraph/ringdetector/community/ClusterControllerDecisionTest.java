package com.fraudgraph.ringdetector.community;

import com.fraudgraph.ringdetector.decision.ClusterDecision;
import com.fraudgraph.ringdetector.decision.ClusterDecisionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Phase 4 audit-log endpoint (docs/BUILD_ORDER.md Phase 4: Confirm/Dismiss
 * decisions written to Postgres). Requires Postgres already running
 * (docker compose -f infra/docker-compose.yml up -d postgres).
 *
 * @Transactional rolls back each test's writes, so the real dev database stays clean
 * between runs instead of accumulating test rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "scheduling.enabled=false")
class ClusterControllerDecisionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClusterDecisionRepository clusterDecisionRepository;

    @Test
    void recordsAValidDecision() throws Exception {
        mockMvc.perform(post("/api/clusters/401/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"CONFIRM\", \"reason\": \"matches fan-in mule pattern\"}"))
            .andExpect(status().isOk());

        List<ClusterDecision> decisions = clusterDecisionRepository.findByCommunityIdOrderByDecidedAtDesc(401);
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).getDecision()).isEqualTo(ClusterDecision.Decision.CONFIRM);
        assertThat(decisions.get(0).getReason()).isEqualTo("matches fan-in mule pattern");
    }

    @Test
    void rejectsADecisionMissingAReason() throws Exception {
        mockMvc.perform(post("/api/clusters/401/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"DISMISS\", \"reason\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADecisionMissingTheDecisionValue() throws Exception {
        mockMvc.perform(post("/api/clusters/401/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"looks suspicious\"}"))
            .andExpect(status().isBadRequest());
    }
}
