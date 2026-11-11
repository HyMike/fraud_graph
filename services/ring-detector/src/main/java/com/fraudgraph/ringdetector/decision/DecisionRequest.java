package com.fraudgraph.ringdetector.decision;

/**
 * memberFingerprint is the CommunityScore.memberFingerprint the investigator was
 * shown; the server rejects the decision if the community has changed since.
 *
 * Who decided is not part of the request: it is the authenticated user from the
 * JWT, so it cannot be spoofed by the client.
 */
public record DecisionRequest(
    ClusterDecision.Decision decision,
    String reason,
    String memberFingerprint) {
}
