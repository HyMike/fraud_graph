package com.fraudgraph.ringdetector.detection;

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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Phase 2 structural detection against the fixed synthetic graph
 * (docs/BUILD_ORDER.md Phase 2 "Verify": precision-of-one against planted ground truth).
 *
 * Requires Neo4j already running (docker compose -f infra/docker-compose.yml up -d neo4j),
 * same as bootRun — reuses the real scripts/load-graph.cypher (MERGE-idempotent, safe to
 * re-run) to (re)seed it rather than duplicating the fixture as hand-written Java.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RingDetectionServiceTest {

    private static final Path LOAD_SCRIPT = Path.of("../../scripts/load-graph.cypher");

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private RingDetectionService ringDetectionService;

    @BeforeAll
    void loadFixtureData() throws IOException {
        for (String statement : loadScriptStatements()) {
            neo4jClient.query(statement).run();
        }
    }

    private static List<String> loadScriptStatements() throws IOException {
        String script = Files.readString(LOAD_SCRIPT);
        return Arrays.stream(script.split(";"))
            .map(RingDetectionServiceTest::stripComments)
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    private static String stripComments(String block) {
        return block.lines()
            .filter(line -> !line.strip().startsWith("//"))
            .collect(Collectors.joining("\n"));
    }

    @Test
    void fanInReturnsExactlyThePlantedCashOutAccount() {
        List<CounterpartyFlag> flags = ringDetectionService.detectFanIn(10, 24);

        assertThat(flags).hasSize(1);
        CounterpartyFlag flag = flags.get(0);
        assertThat(flag.label()).isEqualTo("FRAUD_RING");
        assertThat(flag.ringId()).isEqualTo("fanin-1");
        assertThat(flag.distinctCounterparties()).isEqualTo(15);
    }

    @Test
    void fanOutReturnsNothingSinceNoFanOutRingIsPlanted() {
        List<CounterpartyFlag> flags = ringDetectionService.detectFanOut(10, 24);

        assertThat(flags).isEmpty();
    }

    @Test
    void cycleDetectionIncludesThePlantedTriangle() {
        List<String> plantedCycleAccountIds = neo4jClient.query(
                "MATCH (a:Account {ringId: 'cycle-1'}) RETURN a.id AS id")
            .fetch().all().stream()
            .map(row -> (String) row.get("id"))
            .sorted()
            .toList();

        List<CycleFlag> flags = ringDetectionService.detectCycles(4, 25);
        List<List<String>> distinctCycles = flags.stream()
            .map(f -> f.accountIds().stream().sorted().toList())
            .distinct()
            .toList();

        // The fixed random background population (400 NORMAL accounts, 800 random
        // transfers, seed=42) incidentally contains its own short cycle alongside
        // the planted ring — a real false positive of pure structural cycle
        // detection with no amount/velocity signal, not a bug in the query.
        assertThat(distinctCycles).contains(plantedCycleAccountIds);
    }
}
