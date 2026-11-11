package com.fraudgraph.ringdetector.controller;

import com.fraudgraph.ringdetector.dto.response.ClusterSnapshot;
import com.fraudgraph.ringdetector.dto.response.CommunityScore;
import com.fraudgraph.ringdetector.entity.ClusterDecision;
import com.fraudgraph.ringdetector.repository.ClusterDecisionRepository;
import com.fraudgraph.ringdetector.service.CommunityDetectionService;
import com.fraudgraph.ringdetector.util.ClusterFingerprint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Phase 4 audit-log endpoint (docs/BUILD_ORDER.md Phase 4: Confirm/Dismiss
 * decisions written to Postgres), including the membership snapshot that keeps a
 * decision tied to the exact accounts reviewed even though Louvain renumbers
 * community IDs on every recompute.
 *
 * Requires Neo4j and Postgres already running
 * (docker compose -f infra/docker-compose.yml up -d neo4j postgres).
 *
 * Requests carry a mock JWT (spring-security-test) for an ANALYST named alice;
 * role-based access rules themselves are covered in SecurityAccessTest.
 *
 * @Transactional rolls back each test's writes, so the real dev database stays clean
 * between runs instead of accumulating test rows.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "scheduling.enabled=false")
class ClusterControllerDecisionTest {

    private static final Path LOAD_SCRIPT = Path.of("../../scripts/load-graph.cypher");
    private static final String FAN_IN_HUB = "acct_0401";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private CommunityDetectionService communityDetectionService;

    @Autowired
    private ClusterDecisionRepository clusterDecisionRepository;

    private CommunityScore fanInCluster;

    // The dev database may already hold decisions for the same community ID from
    // manual dashboard use; tests only look at rows written after this snapshot.
    private Set<Long> preExistingDecisionIds;

    @BeforeEach
    void rememberExistingDecisions() {
        preExistingDecisionIds = clusterDecisionRepository.findAll().stream()
            .map(ClusterDecision::getId)
            .collect(Collectors.toSet());
    }

    private List<ClusterDecision> newDecisionsFor(long communityId) {
        return clusterDecisionRepository.findByCommunityIdOrderByDecidedAtDesc(communityId).stream()
            .filter(d -> !preExistingDecisionIds.contains(d.getId()))
            .toList();
    }

    @BeforeAll
    void loadFixtureAndRecompute() throws IOException {
        for (String statement : loadScriptStatements()) {
            neo4jClient.query(statement).run();
        }
        List<CommunityScore> scores = communityDetectionService.recomputeClusters();
        fanInCluster = scores.stream()
            .filter(s -> FAN_IN_HUB.equals(s.hubAccountId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no community with the fan-in hub as its hub"));
    }

    @Test
    void recordsADecisionWithWhoDecidedAndASnapshotOfTheMembers() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CONFIRM", "matches fan-in mule pattern", fanInCluster.memberFingerprint())))
            .andExpect(status().isOk());

        List<ClusterDecision> decisions = newDecisionsFor(fanInCluster.communityId());
        assertThat(decisions).hasSize(1);
        ClusterDecision decision = decisions.get(0);
        assertThat(decision.getDecision()).isEqualTo(ClusterDecision.Decision.CONFIRM);
        assertThat(decision.getReason()).isEqualTo("matches fan-in mule pattern");
        assertThat(decision.getDecidedBy()).isEqualTo("alice");
        assertThat(decision.getMemberFingerprint()).isEqualTo(fanInCluster.memberFingerprint());
        // 15 mules + 1 cash-out account, frozen as they were when the decision was made.
        assertThat(decision.getMemberAccountIds()).hasSize(16).contains(FAN_IN_HUB);
        assertThat(ClusterFingerprint.of(decision.getMemberAccountIds())).isEqualTo(decision.getMemberFingerprint());
    }

    @Test
    void snapshotsTheTransfersAndDevicesOfTheReviewedCluster() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CONFIRM", "mule fan-in", fanInCluster.memberFingerprint())))
            .andExpect(status().isOk());

        ClusterSnapshot snapshot = newDecisionsFor(fanInCluster.communityId()).get(0).getClusterSnapshot();
        assertThat(snapshot.accountIds()).hasSize(16).contains(FAN_IN_HUB);
        // Every mule pays the hub, so there is at least one transfer into it per mule.
        assertThat(snapshot.transfers())
            .filteredOn(t -> FAN_IN_HUB.equals(t.toAccountId()))
            .hasSizeGreaterThanOrEqualTo(15);
        assertThat(snapshot.transfers()).allSatisfy(t -> {
            assertThat(snapshot.accountIds()).contains(t.fromAccountId(), t.toAccountId());
            assertThat(t.id()).isNotBlank();
            assertThat(t.amount()).isPositive();
            assertThat(t.ts()).isNotBlank();
        });
        assertThat(snapshot.deviceUses()).allSatisfy(use -> {
            assertThat(snapshot.accountIds()).contains(use.accountId());
            assertThat(snapshot.devices()).anyMatch(d -> d.id().equals(use.deviceId()));
        });
    }

    @Test
    void recordsTheAuthenticatedUserAndIgnoresADecidedByInTheBody() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"DISMISS\", \"reason\": \"r\", \"decidedBy\": \"mallory\", \"memberFingerprint\": \"%s\"}"
                    .formatted(fanInCluster.memberFingerprint())))
            .andExpect(status().isOk());

        assertThat(newDecisionsFor(fanInCluster.communityId()))
            .singleElement()
            .extracting(ClusterDecision::getDecidedBy)
            .isEqualTo("alice");
    }

    @Test
    void rejectsADecisionWhenTheClusterChangedSinceItWasLoaded() throws Exception {
        String staleFingerprint = ClusterFingerprint.of(List.of("acct_9999"));

        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("CONFIRM", "looked like a ring earlier", staleFingerprint)))
            .andExpect(status().isConflict());

        assertThat(newDecisionsFor(fanInCluster.communityId()))
            .isEmpty();
    }

    @Test
    void rejectsADecisionForACommunityThatNoLongerExists() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", -1)
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("DISMISS", "noise", fanInCluster.memberFingerprint())))
            .andExpect(status().isConflict());
    }

    @Test
    void rejectsADecisionMissingTheFingerprint() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"CONFIRM\", \"reason\": \"r\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADecisionMissingAReason() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("DISMISS", "", fanInCluster.memberFingerprint())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsADecisionMissingTheDecisionValue() throws Exception {
        mockMvc.perform(post("/api/clusters/{id}/decision", fanInCluster.communityId())
                .with(analyst("alice"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"looks suspicious\", \"memberFingerprint\": \"%s\"}"
                    .formatted(fanInCluster.memberFingerprint())))
            .andExpect(status().isBadRequest());
    }

    private static String body(String decision, String reason, String fingerprint) {
        return "{\"decision\": \"%s\", \"reason\": \"%s\", \"memberFingerprint\": \"%s\"}"
            .formatted(decision, reason, fingerprint);
    }

    private static RequestPostProcessor analyst(String username) {
        return jwt().jwt(token -> token.subject(username))
            .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"));
    }

    private static List<String> loadScriptStatements() throws IOException {
        String script = Files.readString(LOAD_SCRIPT);
        return Arrays.stream(script.split(";"))
            .map(block -> block.lines()
                .filter(line -> !line.strip().startsWith("//"))
                .collect(Collectors.joining("\n")))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
