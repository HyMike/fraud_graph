package com.fraudgraph.ringdetector.community;

/**
 * A Louvain community scored for suspiciousness from pure graph structure
 * (density, shared-device ratio, cluster size) — not a trained model.
 * hubAccountId is the highest-PageRank account within the community.
 */
public record CommunityScore(
    long communityId,
    long clusterSize,
    double density,
    double sharedDeviceRatio,
    double suspiciousnessScore,
    String hubAccountId,
    double hubPagerank) {
}
