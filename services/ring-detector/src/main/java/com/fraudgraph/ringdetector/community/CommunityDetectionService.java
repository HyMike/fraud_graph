package com.fraudgraph.ringdetector.community;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3 community detection: Louvain (community membership) + PageRank (centrality),
 * batch/periodic via Neo4j GDS — never triggered per-transaction (see CLAUDE.md).
 * Suspiciousness scoring is a small hand-weighted formula over graph structure
 * (density, shared-device ratio, cluster size), not a trained model.
 */
@Service
public class CommunityDetectionService {

    private static final Logger log = LoggerFactory.getLogger(CommunityDetectionService.class);
    private static final String GRAPH_NAME = "ringDetectorGraph";

    // Weights are hand-picked, not fit: density and shared-device signal are
    // weighted equally since either alone can indicate a ring; cluster size
    // contributes a smooth, small bonus toward tighter (smaller) clusters
    // without a hard cutoff, since normal traffic tends to form large, sprawling
    // communities and rings tend to be small and self-contained.
    private static final double DENSITY_WEIGHT = 0.4;
    private static final double SHARED_DEVICE_WEIGHT = 0.4;
    private static final double SIZE_WEIGHT = 0.2;

    // Accounts without a communityId (graph loaded but never recomputed) are skipped.
    private static final String CLUSTER_STATS_QUERY = """
        MATCH (a:Account) WHERE a.communityId IS NOT NULL
        WITH a.communityId AS communityId, count(a) AS clusterSize
        OPTIONAL MATCH (a1:Account)-[:TRANSFER]-(a2:Account)
          WHERE a1.communityId = communityId AND a2.communityId = communityId AND a1.id < a2.id
        WITH communityId, clusterSize, count(DISTINCT [a1.id, a2.id]) AS internalTransferPairs
        OPTIONAL MATCH (a1:Account)-[:USED]->(d:Device)<-[:USED]-(a2:Account)
          WHERE a1.communityId = communityId AND a2.communityId = communityId AND a1 <> a2
        WITH communityId, clusterSize, internalTransferPairs, count(DISTINCT a1.id) AS sharedDeviceAccountCount
        RETURN communityId, clusterSize, internalTransferPairs, sharedDeviceAccountCount
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

    public CommunityDetectionService(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    // Placeholder cadence for the MVP/demo; a production interval would be tuned operationally.
    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void scheduledRecompute() {
        List<CommunityScore> scores = recomputeClusters();
        log.info("Scheduled cluster recompute: {} communities scored", scores.size());
    }

    public List<CommunityScore> recomputeClusters() {
        neo4jClient.query("CALL gds.graph.drop($graphName, false)")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();

        neo4jClient.query("""
                CALL gds.graph.project($graphName, ['Account', 'Device'],
                  {TRANSFER: {orientation: 'UNDIRECTED'}, USED: {orientation: 'UNDIRECTED'}})
                """)
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();

        neo4jClient.query("CALL gds.louvain.write($graphName, {writeProperty: 'communityId'})")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();

        neo4jClient.query("CALL gds.pageRank.write($graphName, {writeProperty: 'pagerank'})")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();

        neo4jClient.query("CALL gds.graph.drop($graphName, false)")
            .bindAll(Map.of("graphName", GRAPH_NAME))
            .run();

        return currentClusters();
    }

    /**
     * Scores from the communityId/pagerank values the last recompute wrote, without
     * running GDS again — cheap enough for any logged-in user to call.
     */
    public List<CommunityScore> currentClusters() {
        Map<Long, long[]> stats = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(CLUSTER_STATS_QUERY).fetch().all()) {
            stats.put((Long) row.get("communityId"), new long[] {
                (Long) row.get("clusterSize"),
                (Long) row.get("internalTransferPairs"),
                (Long) row.get("sharedDeviceAccountCount")
            });
        }

        Map<Long, Object[]> hubs = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(HUB_QUERY).fetch().all()) {
            hubs.put((Long) row.get("communityId"), new Object[] {
                row.get("hubAccountId"), row.get("hubPagerank")
            });
        }

        Map<Long, String> fingerprints = new HashMap<>();
        for (Map<String, Object> row : neo4jClient.query(MEMBERS_BY_COMMUNITY_QUERY).fetch().all()) {
            @SuppressWarnings("unchecked")
            List<String> accountIds = (List<String>) row.get("accountIds");
            fingerprints.put((Long) row.get("communityId"), ClusterFingerprint.of(accountIds));
        }

        return stats.entrySet().stream()
            .map(entry -> toScore(entry.getKey(), entry.getValue(), hubs.get(entry.getKey()),
                fingerprints.get(entry.getKey())))
            .sorted((a, b) -> Double.compare(b.suspiciousnessScore(), a.suspiciousnessScore()))
            .toList();
    }

    /** Accounts, devices and transfers of a community right now; empty lists if it no longer exists. */
    @SuppressWarnings("unchecked")
    public ClusterSnapshot getClusterSnapshot(long communityId) {
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

    private static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private CommunityScore toScore(long communityId, long[] stat, Object[] hub, String memberFingerprint) {
        long clusterSize = stat[0];
        long internalTransferPairs = stat[1];
        long sharedDeviceAccountCount = stat[2];

        double maxPossiblePairs = clusterSize * (clusterSize - 1) / 2.0;
        double density = maxPossiblePairs > 0 ? internalTransferPairs / maxPossiblePairs : 0.0;
        double sharedDeviceRatio = clusterSize > 0 ? (double) sharedDeviceAccountCount / clusterSize : 0.0;
        double sizeFactor = 1.0 / (1 + clusterSize);

        double score = DENSITY_WEIGHT * density
            + SHARED_DEVICE_WEIGHT * sharedDeviceRatio
            + SIZE_WEIGHT * sizeFactor;

        String hubAccountId = hub != null ? (String) hub[0] : null;
        double hubPagerank = hub != null && hub[1] != null ? (Double) hub[1] : 0.0;

        return new CommunityScore(communityId, clusterSize, density, sharedDeviceRatio, score, hubAccountId, hubPagerank,
            memberFingerprint);
    }

    public ClusterGraphResponse getClusterGraph(long communityId) {
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
}
