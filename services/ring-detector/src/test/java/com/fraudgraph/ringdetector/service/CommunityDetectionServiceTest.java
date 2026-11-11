package com.fraudgraph.ringdetector.service;

import com.fraudgraph.ringdetector.dto.response.ClusterGraphResponse;
import com.fraudgraph.ringdetector.dto.response.CommunityScore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Phase 3 community detection against the fixed synthetic graph
 * (docs/BUILD_ORDER.md Phase 3 "Verify": each planted ring lands in its own
 * community distinct from normal traffic; PageRank ranks the hub account highest).
 *
 * Requires Neo4j already running (docker compose -f infra/docker-compose.yml up -d neo4j),
 * same as bootRun — reuses the real scripts/load-graph.cypher to (re)seed it, same
 * approach as RingDetectionServiceTest.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "scheduling.enabled=false")
class CommunityDetectionServiceTest {

    private static final Path LOAD_SCRIPT = Path.of("../../scripts/load-graph.cypher");

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private CommunityDetectionService communityDetectionService;

    private List<CommunityScore> scores;

    @BeforeAll
    void loadFixtureAndRecompute() throws IOException {
        for (String statement : loadScriptStatements()) {
            neo4jClient.query(statement).run();
        }
        scores = communityDetectionService.recomputeClusters();
    }

    private static List<String> loadScriptStatements() throws IOException {
        String script = Files.readString(LOAD_SCRIPT);
        return Arrays.stream(script.split(";"))
            .map(CommunityDetectionServiceTest::stripComments)
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static String stripComments(String block) {
        return block.lines()
            .filter(line -> !line.strip().startsWith("//"))
            .collect(Collectors.joining("\n"));
    }

    private long communityIdOf(String accountId) {
        Map<String, Object> row = neo4jClient.query("MATCH (a:Account {id: $id}) RETURN a.communityId AS communityId")
            .bindAll(Map.of("id", accountId))
            .fetch().one().orElseThrow();
        return (Long) row.get("communityId");
    }

    @Test
    void eachPlantedRingLandsInASingleCommunityDistinctFromNormalTraffic() {
        assertRingIsCohesiveAndDistinctFromNormal("fanin-1");
        assertRingIsCohesiveAndDistinctFromNormal("cycle-1");
        assertRingIsCohesiveAndDistinctFromNormal("devcluster-1");
    }

    private void assertRingIsCohesiveAndDistinctFromNormal(String ringId) {
        List<Long> communityIds = neo4jClient.query(
                "MATCH (a:Account {ringId: $ringId}) RETURN a.communityId AS communityId")
            .bindAll(Map.of("ringId", ringId))
            .fetch().all().stream()
            .map(row -> (Long) row.get("communityId"))
            .distinct()
            .toList();

        assertThat(communityIds).as("ring %s should land in a single community", ringId).hasSize(1);

        long normalCommunityId = communityIdOf("acct_0001");
        assertThat(communityIds.get(0))
            .as("ring %s should be distinct from normal traffic's community", ringId)
            .isNotEqualTo(normalCommunityId);
    }

    @Test
    void eachLookAlikeLandsInItsOwnCommunityDistinctFromNormalTraffic() {
        assertRingIsCohesiveAndDistinctFromNormal("lookalike-family-1");
        assertRingIsCohesiveAndDistinctFromNormal("lookalike-business-1");
    }

    @Test
    void everyPlantedRingRanksAboveTheLookAlikesAndNormalTraffic() {
        Set<Long> ringCommunityIds = neo4jClient.query(
                "MATCH (a:Account {label: 'FRAUD_RING'}) RETURN DISTINCT a.communityId AS communityId")
            .fetch().all().stream()
            .map(row -> (Long) row.get("communityId"))
            .collect(Collectors.toSet());
        assertThat(ringCommunityIds).hasSize(3);

        List<Long> topThree = scores.stream().limit(3).map(CommunityScore::communityId).toList();
        assertThat(topThree)
            .as("the three planted rings should be the three highest-scored clusters")
            .containsExactlyInAnyOrderElementsOf(ringCommunityIds);

        double lowestRingScore = scores.stream()
            .filter(s -> ringCommunityIds.contains(s.communityId()))
            .mapToDouble(CommunityScore::suspiciousnessScore).min().orElseThrow();
        double highestOtherScore = scores.stream()
            .filter(s -> !ringCommunityIds.contains(s.communityId()))
            .mapToDouble(CommunityScore::suspiciousnessScore).max().orElseThrow();
        assertThat(lowestRingScore).isGreaterThan(highestOtherScore);
    }

    @Test
    void singleAccountCommunitiesAreNotScored() {
        assertThat(scores).isNotEmpty().allMatch(s -> s.clusterSize() >= 2);
    }

    @Test
    void fanInRingIsCaughtByItsStarShapeDespiteLowDensity() {
        long fanInCommunityId = communityIdOf("acct_0401");
        CommunityScore fanIn = scores.stream()
            .filter(s -> s.communityId() == fanInCommunityId)
            .findFirst().orElseThrow();

        // 15 of the 120 possible pairs transacted, all into acct_0401.
        assertThat(fanIn.density()).isLessThan(0.2);
        assertThat(fanIn.starScore()).isGreaterThan(0.8);
    }

    @Test
    void pageRankRanksTheFanInHubHighestWithinItsCommunity() {
        long fanInCommunityId = communityIdOf("acct_0401");

        CommunityScore fanInScore = scores.stream()
            .filter(s -> s.communityId() == fanInCommunityId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no score found for fan-in community"));

        assertThat(fanInScore.hubAccountId()).isEqualTo("acct_0401");
    }

    @Test
    void clusterGraphForFanInReturnsAllMembersAndTheirRealEdges() {
        long fanInCommunityId = communityIdOf("acct_0401");

        ClusterGraphResponse graph = communityDetectionService.getClusterGraph(fanInCommunityId);

        // 15 mules + 1 cash-out account, each with its own (unshared) device.
        long accountNodes = graph.nodes().stream().filter(n -> n.type().equals("Account")).count();
        long deviceNodes = graph.nodes().stream().filter(n -> n.type().equals("Device")).count();
        assertThat(accountNodes).isEqualTo(16);
        assertThat(deviceNodes).isEqualTo(16);

        long transferEdges = graph.edges().stream().filter(e -> e.relationshipType().equals("TRANSFER")).count();
        long usedEdges = graph.edges().stream().filter(e -> e.relationshipType().equals("USED")).count();
        assertThat(transferEdges).isEqualTo(15);
        assertThat(usedEdges).isEqualTo(16);
    }
}
