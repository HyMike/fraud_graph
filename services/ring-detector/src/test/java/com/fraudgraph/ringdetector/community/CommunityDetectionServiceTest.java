package com.fraudgraph.ringdetector.community;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    void pageRankRanksTheFanInHubHighestWithinItsCommunity() {
        long fanInCommunityId = communityIdOf("acct_0401");

        CommunityScore fanInScore = scores.stream()
            .filter(s -> s.communityId() == fanInCommunityId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("no score found for fan-in community"));

        assertThat(fanInScore.hubAccountId()).isEqualTo("acct_0401");
    }
}
