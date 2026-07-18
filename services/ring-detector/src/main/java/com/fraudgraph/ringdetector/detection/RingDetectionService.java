package com.fraudgraph.ringdetector.detection;

import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 structural pattern detection: fan-in, fan-out, and bounded-length cycles.
 * Local-subgraph Cypher only, no GDS — this is the near-real-time fast path
 * (see CLAUDE.md); community detection is Phase 3.
 */
@Service
public class RingDetectionService {

    private static final int MIN_CYCLE_LENGTH = 2;
    private static final int MAX_CYCLE_LENGTH = 6;

    // Duration values in Cypher have no total order (< <= > >= are not supported),
    // so the window check is done as plain epoch-millisecond arithmetic instead.
    private static final String FAN_IN_QUERY = """
        MATCH (sender:Account)-[t:TRANSFER]->(receiver:Account)
        WITH receiver, count(DISTINCT sender) AS distinctSenders,
             min(t.ts) AS earliestTs, max(t.ts) AS latestTs
        WHERE distinctSenders >= $minDistinct
          AND (latestTs.epochMillis - earliestTs.epochMillis) <= $windowMillis
        RETURN receiver.id AS accountId, receiver.label AS label, receiver.ringId AS ringId,
               distinctSenders AS distinctCounterparties
        ORDER BY distinctSenders DESC
        """;

    private static final String FAN_OUT_QUERY = """
        MATCH (sender:Account)-[t:TRANSFER]->(receiver:Account)
        WITH sender, count(DISTINCT receiver) AS distinctReceivers,
             min(t.ts) AS earliestTs, max(t.ts) AS latestTs
        WHERE distinctReceivers >= $minDistinct
          AND (latestTs.epochMillis - earliestTs.epochMillis) <= $windowMillis
        RETURN sender.id AS accountId, sender.label AS label, sender.ringId AS ringId,
               distinctReceivers AS distinctCounterparties
        ORDER BY distinctReceivers DESC
        """;

    private final Neo4jClient neo4jClient;

    public RingDetectionService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public List<CounterpartyFlag> detectFanIn(long minDistinctSenders, long windowHours) {
        return runCounterpartyQuery(FAN_IN_QUERY, minDistinctSenders, windowHours);
    }

    public List<CounterpartyFlag> detectFanOut(long minDistinctReceivers, long windowHours) {
        return runCounterpartyQuery(FAN_OUT_QUERY, minDistinctReceivers, windowHours);
    }

    private List<CounterpartyFlag> runCounterpartyQuery(String query, long minDistinct, long windowHours) {
        Collection<Map<String, Object>> rows = neo4jClient.query(query)
            .bindAll(Map.of("minDistinct", minDistinct, "windowMillis", windowHours * 3_600_000L))
            .fetch()
            .all();

        return rows.stream()
            .map(row -> new CounterpartyFlag(
                (String) row.get("accountId"),
                (String) row.get("label"),
                (String) row.get("ringId"),
                (Long) row.get("distinctCounterparties")))
            .toList();
    }

    public List<CycleFlag> detectCycles(int maxLength, int limit) {
        if (maxLength < MIN_CYCLE_LENGTH || maxLength > MAX_CYCLE_LENGTH) {
            throw new IllegalArgumentException(
                "maxLength must be between %d and %d".formatted(MIN_CYCLE_LENGTH, MAX_CYCLE_LENGTH));
        }

        // Variable-length relationship bounds (*2..N) cannot be parameterized in Cypher,
        // so maxLength is interpolated after being validated/clamped above.
        String query = """
            MATCH p = (a:Account)-[:TRANSFER*2..%d]->(a)
            WITH DISTINCT [n IN nodes(p)[0..-1] | n.id] AS accountIds
            RETURN accountIds
            LIMIT $limit
            """.formatted(maxLength);

        Collection<Map<String, Object>> rows = neo4jClient.query(query)
            .bindAll(Map.of("limit", limit))
            .fetch()
            .all();

        return rows.stream()
            .map(row -> new CycleFlag(castAccountIds(row.get("accountIds"))))
            .toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> castAccountIds(Object value) {
        return (List<String>) value;
    }
}
