package com.fraudgraph.ringdetector.dto.response;

/** relationshipType is "TRANSFER" or "USED". */
public record ClusterEdge(String source, String target, String relationshipType) {
}
