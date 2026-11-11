package com.fraudgraph.ringdetector.service;

import com.fraudgraph.ringdetector.dto.response.ClusterGraphResponse;
import com.fraudgraph.ringdetector.dto.response.ClusterSnapshot;
import com.fraudgraph.ringdetector.dto.response.CommunityScore;
import com.fraudgraph.ringdetector.repository.CommunityGraphRepository.ClusterStats;
import com.fraudgraph.ringdetector.repository.CommunityGraphRepository.Hub;
import com.fraudgraph.ringdetector.repository.CommunityGraphRepository;
import com.fraudgraph.ringdetector.util.ClusterFingerprint;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.fraudgraph.ringdetector.constants.DetectionConstants.DENSITY_WEIGHT;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.SHARED_DEVICE_WEIGHT;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.SIZE_WEIGHT;

/**
 * Phase 3 community detection: Louvain (community membership) + PageRank (centrality),
 * batch/periodic via Neo4j GDS — never triggered per-transaction (see CLAUDE.md).
 * Suspiciousness scoring is a small hand-weighted formula over graph structure
 * (density, shared-device ratio, cluster size), not a trained model.
 */
@Service
public class CommunityDetectionService {

    private final CommunityGraphRepository communityGraphRepository;

    public CommunityDetectionService(CommunityGraphRepository communityGraphRepository) {
        this.communityGraphRepository = communityGraphRepository;
    }

    /** Re-runs Louvain + PageRank over the whole graph, then scores the new clusters. */
    public List<CommunityScore> recomputeClusters() {
        communityGraphRepository.writeCommunitiesAndPagerank();
        return currentClusters();
    }

    /**
     * Scores from the communityId/pagerank values the last recompute wrote, without
     * running GDS again — cheap enough for any logged-in user to call.
     */
    public List<CommunityScore> currentClusters() {
        Map<Long, Hub> hubs = communityGraphRepository.findHubs();
        Map<Long, List<String>> members = communityGraphRepository.findMemberAccountIds();

        return communityGraphRepository.findClusterStats().stream()
            .map(stats -> toScore(stats, hubs.get(stats.communityId()),
                ClusterFingerprint.of(members.get(stats.communityId()))))
            .sorted(Comparator.comparingDouble(CommunityScore::suspiciousnessScore).reversed())
            .toList();
    }

    /** Accounts, devices and transfers of a community right now; empty lists if it no longer exists. */
    public ClusterSnapshot getClusterSnapshot(long communityId) {
        return communityGraphRepository.findClusterSnapshot(communityId);
    }

    public ClusterGraphResponse getClusterGraph(long communityId) {
        return communityGraphRepository.findClusterGraph(communityId);
    }

    private static CommunityScore toScore(ClusterStats stats, Hub hub, String memberFingerprint) {
        long clusterSize = stats.clusterSize();

        double maxPossiblePairs = clusterSize * (clusterSize - 1) / 2.0;
        double density = maxPossiblePairs > 0 ? stats.internalTransferPairs() / maxPossiblePairs : 0.0;
        double sharedDeviceRatio = clusterSize > 0 ? (double) stats.sharedDeviceAccountCount() / clusterSize : 0.0;
        double sizeFactor = 1.0 / (1 + clusterSize);

        double score = DENSITY_WEIGHT * density
            + SHARED_DEVICE_WEIGHT * sharedDeviceRatio
            + SIZE_WEIGHT * sizeFactor;

        String hubAccountId = hub != null ? hub.accountId() : null;
        double hubPagerank = hub != null && hub.pagerank() != null ? hub.pagerank() : 0.0;

        return new CommunityScore(stats.communityId(), clusterSize, density, sharedDeviceRatio, score,
            hubAccountId, hubPagerank, memberFingerprint);
    }
}
