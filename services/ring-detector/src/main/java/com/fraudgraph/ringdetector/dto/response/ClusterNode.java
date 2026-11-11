package com.fraudgraph.ringdetector.dto.response;

/** type is "Account" or "Device" — label/ringId are null for Device nodes. */
public record ClusterNode(String id, String type, String label, String ringId, double pagerank) {
}
