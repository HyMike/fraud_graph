package com.fraudgraph.ringdetector.decision;

public record DecisionRequest(ClusterDecision.Decision decision, String reason) {
}
