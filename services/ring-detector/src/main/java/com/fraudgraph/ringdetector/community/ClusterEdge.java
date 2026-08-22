package com.fraudgraph.ringdetector.community;

/** relationshipType is "TRANSFER" or "USED". */
public record ClusterEdge(String source, String target, String relationshipType) {
}
