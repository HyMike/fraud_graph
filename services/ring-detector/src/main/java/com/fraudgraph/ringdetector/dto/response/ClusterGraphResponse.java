package com.fraudgraph.ringdetector.dto.response;

import java.util.List;

/** Real nodes/edges for one community, for Cytoscape rendering — CommunityScore only has aggregates. */
public record ClusterGraphResponse(long communityId, List<ClusterNode> nodes, List<ClusterEdge> edges) {
}
