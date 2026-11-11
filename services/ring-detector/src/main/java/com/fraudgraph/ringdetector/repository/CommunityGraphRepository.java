package com.fraudgraph.ringdetector.repository;

import com.fraudgraph.ringdetector.dto.response.ClusterEdge;
import com.fraudgraph.ringdetector.dto.response.ClusterGraphResponse;
import com.fraudgraph.ringdetector.dto.response.ClusterNode;
import com.fraudgraph.ringdetector.dto.response.ClusterSnapshot;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.fraudgraph.ringdetector.constants.DetectionConstants.GDS_GRAPH_NAME;

/**
 * Neo4j access for Phase 3 community detection: runs Louvain + PageRank through
 * GDS (writing communityId/pagerank back onto the nodes) and reads clusters back
 * out. Scoring lives in CommunityDetectionService; this class only runs Cypher
 * and maps rows.
 */
@Repository
public class CommunityGraphRepository {

    /**
     * Raw per-community counts that the suspiciousness score is computed from.
     * topReceiverSenderCount: the most distinct members that sent money to any one member.
     * openingSpanDays: days between the first and last member account opening (null if unknown).
     */
    public record ClusterStats(long communityId, long clusterSize, long internalTransferPairs,
                               long sharedDeviceAccountCount, long topReceiverSenderCount,
                               Long openingSpanDays) {
    }

    /** The highest-PageRank account of a community. */
    public record Hub(String accountId, Double pagerank) {
    }

    // Accounts without a communityId (graph loaded but never recomputed) are skipped.
    // Each CALL subquery aggregates per community independently, so one signal's
    // OPTIONAL MATCH can't multiply the rows another signal counts.
    private static final String CLUSTER_STATS_QUERY = """
        MATCH (a:Account) WHERE a.communityId IS NOT NULL
        WITH a.communityId AS communityId, count(a) AS clusterSize,
             min(a.openedAt) AS firstOpened, max(a.openedAt) AS lastOpened
        CALL {
          WITH communityId
          OPTIONAL MATCH (a1:Account)-[:TRANSFER]-(a2:Account)
            WHERE a1.communityId = communityId AND a2.communityId = communityId AND a1.id < a2.id
          RETURN count(DISTINCT [a1.id, a2.id]) AS internalTransferPairs
        }
        CALL {
          WITH communityId
          OPTIONAL MATCH (a1:Account)-[:USED]->(:Device)<-[:USED]-(a2:Account)
            WHERE a1.communityId = communityId AND a2.communityId = communityId AND a1 <> a2
          RETURN count(DISTINCT a1.id) AS sharedDeviceAccountCount
        }
        CALL {
          WITH communityId
          OPTIONAL MATCH (s:Account)-[:TRANSFER]->(r:Account)
            WHERE s.communityId = communityId AND r.communityId = communityId AND s <> r
          WITH r, count(DISTINCT s) AS senders
          RETURN coalesce(max(senders), 0) AS topReceiverSenderCount
        }
        RETURN communityId, clusterSize, internalTransferPairs, sharedDeviceAccountCount,
               topReceiverSenderCount,
               CASE WHEN firstOpened IS NULL THEN null
                    ELSE duration.inDays(firstOpened, lastOpened).days END AS openingSpanDays
        """;

    // ORDER BY immediately before collect() preserves order within the collected list,
    // so collect(a)[0] is the highest-pagerank account per community.
    private static final String HUB_QUERY = """
        MATCH (a:Account)
        WITH a.communityId AS communityId, a
        ORDER BY a.pagerank DESC
        WITH communityId, collect(a)[0] AS hub
        RETURN communityId, hub.id AS hubAccountId, hub.pagerank AS hubPagerank
        """;

    private static final String MEMBERS_BY_COMMUNITY_QUERY = """
        MATCH (a:Account)
        RETURN a.communityId AS communityId, collect(a.id) AS accountIds
        """;

    private static final String CLUSTER_NODES_QUERY = """
        MATCH (n) WHERE (n:Account OR n:Device) AND n.communityId = $communityId
        RETURN n.id AS id, labels(n)[0] AS type, n.label AS label, n.ringId AS ringId, n.pagerank AS pagerank
        """;

    // Edges are returned with their real stored direction (unlike the undirected
    // GDS projection used for Louvain/PageRank), since this is for display.
    private static final String CLUSTER_EDGES_QUERY = """
        MATCH (a:Account)-[:TRANSFER]->(b:Account)
          WHERE a.communityId = $communityId AND b.communityId = $communityId
        RETURN a.id AS source, b.id AS target, 'TRANSFER' AS relationshipType
        UNION
        MATCH (a:Account)-[:USED]->(d:Device)
          WHERE a.communityId = $communityId AND d.communityId = $communityId
        RETURN a.id AS source, d.id AS target, 'USED' AS relationshipType
        """;

    // One query, so the accounts, devices and transfers all come from the same
    // instant — separate queries could straddle a concurrent Louvain recompute.
    // Devices are filtered by communityId to match what CLUSTER_EDGES_QUERY shows.
    private static final String CLUSTER_SNAPSHOT_QUERY = """
        MATCH (a:Account) WHERE a.communityId = $communityId
        WITH a ORDER BY a.id
        WITH collect(a) AS accounts
        CALL {
          WITH accounts
          UNWIND accounts AS a
          MATCH (a)-[t:TRANSFER]->(b:Account) WHERE b.communityId = $communityId
          WITH a, b, t ORDER BY t.ts, t.id
          RETURN collect({id: t.id, fromAccountId: a.id, toAccountId: b.id,
                          amount: t.amount, ts: toString(t.ts)}) AS transfers
        }
        CALL {
          WITH accounts
          UNWIND accounts AS a
          MATCH (a)-[:USED]->(d:Device) WHERE d.communityId = $communityId
          WITH a, d ORDER BY d.id, a.id
          RETURN collect(DISTINCT {id: d.id, fingerprint: d.fingerprint}) AS devices,
                 collect({accountId: a.id, deviceId: d.id}) AS deviceUses
        }
        RETURN [a IN accounts | {id: a.id, label: a.label, ringId: a.ringId, pagerank: a.pagerank}] AS accounts,
               devices, deviceUses, transfers
        """;

    private final Neo4jClient neo4jClient;

    public CommunityGraphRepository(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /**
     * Projects Account/Device into an undirected in-memory GDS graph, writes
     * communityId (Louvain) and pagerank (PageRank) back onto the nodes, then
     * drops the projection. Full-graph and batch only — never per-transaction.
     */
    public void writeCommunitiesAndPagerank() {
        runOnGdsGraph("CALL gds.graph.drop($graphName, false)");
        runOnGdsGraph("""
            CALL gds.graph.project($graphName, ['Account', 'Device'],
              {TRANSFER: {orientation: 'UNDIRECTED'}, USED: {orientation: 'UNDIRECTED'}})
            """);
        runOnGdsGraph("CALL gds.louvain.write($graphName, {writeProperty: 'communityId'})");
        runOnGdsGraph("CALL gds.pageRank.write($graphName, {writeProperty: 'pagerank'})");
        runOnGdsGraph("CALL gds.graph.drop($graphName, false)");
    }

    public List<ClusterStats> findClusterStats() {
        return neo4jClient.query(CLUSTER_STATS_QUERY).fetch().all().stream()
            .map(row -> new ClusterStats(
                (Long) row.get("communityId"),
                (Long) row.get("clusterSize"),
                (Long) row.get("internalTransferPairs"),
                (Long) row.get("sharedDeviceAccountCount"),
                (Long) row.get("topReceiverSenderCount"),
                (Long) row.get("openingSpanDays")))
            .toList();
    }

    /** Hub account per communityId. */
    public Map<Long, Hub> findHubs() {
        Map<Long, Hub> hubs = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(HUB_QUERY).fetch().all()) {
            hubs.put((Long) row.get("communityId"),
                new Hub((String) row.get("hubAccountId"), (Double) row.get("hubPagerank")));
        }
        return hubs;
    }

    /** Member account IDs per communityId. */
    @SuppressWarnings("unchecked")
    public Map<Long, List<String>> findMemberAccountIds() {
        Map<Long, List<String>> members = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(MEMBERS_BY_COMMUNITY_QUERY).fetch().all()) {
            members.put((Long) row.get("communityId"), (List<String>) row.get("accountIds"));
        }
        return members;
    }

    public ClusterGraphResponse findClusterGraph(long communityId) {
        List<ClusterNode> nodes = neo4jClient.query(CLUSTER_NODES_QUERY)
            .bindAll(Map.of("communityId", communityId))
            .fetch().all().stream()
            .map(row -> new ClusterNode(
                (String) row.get("id"),
                (String) row.get("type"),
                (String) row.get("label"),
                (String) row.get("ringId"),
                row.get("pagerank") != null ? (Double) row.get("pagerank") : 0.0))
            .toList();

        List<ClusterEdge> edges = neo4jClient.query(CLUSTER_EDGES_QUERY)
            .bindAll(Map.of("communityId", communityId))
            .fetch().all().stream()
            .map(row -> new ClusterEdge(
                (String) row.get("source"),
                (String) row.get("target"),
                (String) row.get("relationshipType")))
            .toList();

        return new ClusterGraphResponse(communityId, nodes, edges);
    }

    /** Accounts, devices and transfers of a community right now; empty lists if it no longer exists. */
    @SuppressWarnings("unchecked")
    public ClusterSnapshot findClusterSnapshot(long communityId) {
        Map<String, Object> row = neo4jClient.query(CLUSTER_SNAPSHOT_QUERY)
            .bindAll(Map.of("communityId", communityId))
            .fetch().one()
            .orElseThrow();
        return new ClusterSnapshot(
            ((List<Map<String, Object>>) row.get("accounts")).stream()
                .map(m -> new ClusterSnapshot.Account((String) m.get("id"), (String) m.get("label"),
                    (String) m.get("ringId"), toDouble(m.get("pagerank"))))
                .toList(),
            ((List<Map<String, Object>>) row.get("devices")).stream()
                .map(m -> new ClusterSnapshot.Device((String) m.get("id"), (String) m.get("fingerprint")))
                .toList(),
            ((List<Map<String, Object>>) row.get("deviceUses")).stream()
                .map(m -> new ClusterSnapshot.DeviceUse((String) m.get("accountId"), (String) m.get("deviceId")))
                .toList(),
            ((List<Map<String, Object>>) row.get("transfers")).stream()
                .map(m -> new ClusterSnapshot.Transfer((String) m.get("id"), (String) m.get("fromAccountId"),
                    (String) m.get("toAccountId"), toDouble(m.get("amount")), (String) m.get("ts")))
                .toList());
    }

    private void runOnGdsGraph(String cypher) {
        neo4jClient.query(cypher).bindAll(Map.of("graphName", GDS_GRAPH_NAME)).run();
    }

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }
}
