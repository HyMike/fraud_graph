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

import static com.fraudgraph.ringdetector.constants.DetectionConstants.MIN_RING_SIZE;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.OPENING_BURST_SCALE_DAYS;

/**
 * Phase 3 community detection: Louvain (community membership) + PageRank (centrality),
 * batch/periodic via Neo4j GDS — never triggered per-transaction (see CLAUDE.md).
 *
 * Suspiciousness is a hand-written formula, not a trained model:
 * <pre>
 *   score = ringShape × openingBurst
 *   ringShape    = max(density, sharedDeviceRatio, starScore)  — "is this shaped like a ring?"
 *   openingBurst = 1 / (1 + openingSpanDays / scale)           — "were the accounts opened together?"
 * </pre>
 * Shape alone can't separate a ring from a family sharing a device, since both are
 * small, tight and share hardware; the multiplication requires both the shape and
 * the coordinated timing. ringShape takes the strongest signal rather than a blend
 * because a ring only needs to match one fraud pattern (a fan-in star has low density
 * by definition). Only clusters of at least MIN_RING_SIZE accounts are returned.
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
            .filter(stats -> stats.clusterSize() >= MIN_RING_SIZE)
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

        // Star: one member receives from nearly everyone else, who don't pay each other.
        double topReceiverInflowShare = clusterSize > 1
            ? (double) stats.topReceiverSenderCount() / (clusterSize - 1) : 0.0;
        double starScore = topReceiverInflowShare * (1 - density);

        double ringShape = Math.max(density, Math.max(sharedDeviceRatio, starScore));

        // Unknown opening dates give no evidence of a coordinated batch, so no burst.
        Long openingSpanDays = stats.openingSpanDays();
        double openingBurst = openingSpanDays != null
            ? 1.0 / (1 + openingSpanDays / OPENING_BURST_SCALE_DAYS) : 0.0;

        double score = ringShape * openingBurst;

        String hubAccountId = hub != null ? hub.accountId() : null;
        double hubPagerank = hub != null && hub.pagerank() != null ? hub.pagerank() : 0.0;

        return new CommunityScore(stats.communityId(), clusterSize, density, sharedDeviceRatio,
            starScore, openingSpanDays, score,
            hubAccountId, hubPagerank, memberFingerprint);
    }
}
