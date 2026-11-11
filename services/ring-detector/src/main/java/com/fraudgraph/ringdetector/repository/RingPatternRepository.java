package com.fraudgraph.ringdetector.repository;

import com.fraudgraph.ringdetector.dto.response.CounterpartyFlag;
import com.fraudgraph.ringdetector.dto.response.CycleFlag;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Neo4j access for Phase 2 structural detection: fan-in, fan-out and bounded
 * cycles. Local-subgraph Cypher only, no GDS. Input validation lives in
 * RingDetectionService; this class only runs queries and maps rows.
 */
@Repository
public class RingPatternRepository {

    private static final long MILLIS_PER_HOUR = 3_600_000L;

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

    // Variable-length relationship bounds (*2..N) cannot be parameterized in Cypher,
    // so maxLength is interpolated; callers must validate it first.
    private static final String CYCLES_QUERY_TEMPLATE = """
        MATCH p = (a:Account)-[:TRANSFER*2..%d]->(a)
        WITH DISTINCT [n IN nodes(p)[0..-1] | n.id] AS accountIds
        RETURN accountIds
        LIMIT $limit
        """;

    private final Neo4jClient neo4jClient;

    public RingPatternRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    public List<CounterpartyFlag> findFanIn(long minDistinctSenders, long windowHours) {
        return findCounterpartyFlags(FAN_IN_QUERY, minDistinctSenders, windowHours);
    }

    public List<CounterpartyFlag> findFanOut(long minDistinctReceivers, long windowHours) {
        return findCounterpartyFlags(FAN_OUT_QUERY, minDistinctReceivers, windowHours);
    }

    /** {@code maxLength} must already be validated: it is interpolated into the query text. */
    @SuppressWarnings("unchecked")
    public List<CycleFlag> findCycles(int maxLength, int limit) {
        return neo4jClient.query(CYCLES_QUERY_TEMPLATE.formatted(maxLength))
            .bindAll(Map.of("limit", limit))
            .fetch().all().stream()
            .map(row -> new CycleFlag((List<String>) row.get("accountIds")))
            .toList();
    }

    private List<CounterpartyFlag> findCounterpartyFlags(String query, long minDistinct, long windowHours) {
        return neo4jClient.query(query)
            .bindAll(Map.of("minDistinct", minDistinct, "windowMillis", windowHours * MILLIS_PER_HOUR))
            .fetch().all().stream()
            .map(row -> new CounterpartyFlag(
                (String) row.get("accountId"),
                (String) row.get("label"),
                (String) row.get("ringId"),
                (Long) row.get("distinctCounterparties")))
            .toList();
    }
}
