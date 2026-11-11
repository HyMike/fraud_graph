package com.fraudgraph.ringdetector.community;

/**
 * A Louvain community scored for suspiciousness from pure graph structure
 * (density, shared-device ratio, cluster size) — not a trained model.
 * hubAccountId is the highest-PageRank account within the community.
 * memberFingerprint identifies the exact set of member accounts (see
 * ClusterFingerprint) — communityId alone is renumbered on every recompute.
 */
public record CommunityScore(
    long communityId,
    long clusterSize,
    double density,
    double sharedDeviceRatio,
    double suspiciousnessScore,
    String hubAccountId,
    double hubPagerank,
    String memberFingerprint) {
}
