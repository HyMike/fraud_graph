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

    private static final String CLUSTER_STATS_QUERY = """
        MATCH (a:Account)
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

        return scoreClusters();
    }

    private List<CommunityScore> scoreClusters() {
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

        return stats.entrySet().stream()
            .map(entry -> toScore(entry.getKey(), entry.getValue(), hubs.get(entry.getKey())))
            .sorted((a, b) -> Double.compare(b.suspiciousnessScore(), a.suspiciousnessScore()))
            .toList();
    }

    private CommunityScore toScore(long communityId, long[] stat, Object[] hub) {
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

        return new CommunityScore(communityId, clusterSize, density, sharedDeviceRatio, score, hubAccountId, hubPagerank);
    }
}
