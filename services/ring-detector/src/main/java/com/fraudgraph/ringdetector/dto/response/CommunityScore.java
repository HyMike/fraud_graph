package com.fraudgraph.ringdetector.dto.response;

/**
 * A Louvain community scored for suspiciousness by a hand-written formula over its
 * shape (density, shared-device ratio, star shape) and account-opening timing —
 * not a trained model. openingSpanDays is null when no member has an openedAt.
 * hubAccountId is the highest-PageRank account within the community.
 * memberFingerprint identifies the exact set of member accounts (see
 * ClusterFingerprint) — communityId alone is renumbered on every recompute.
 */
public record CommunityScore(
    long communityId,
    long clusterSize,
    double density,
    double sharedDeviceRatio,
    double starScore,
    Long openingSpanDays,
    double suspiciousnessScore,
    String hubAccountId,
    double hubPagerank,
    String memberFingerprint) {
}
