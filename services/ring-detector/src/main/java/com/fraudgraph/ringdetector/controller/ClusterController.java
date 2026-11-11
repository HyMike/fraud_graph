package com.fraudgraph.ringdetector.controller;

import com.fraudgraph.ringdetector.constants.ApiPaths;
import com.fraudgraph.ringdetector.dto.request.DecisionRequest;
import com.fraudgraph.ringdetector.dto.response.ClusterGraphResponse;
import com.fraudgraph.ringdetector.dto.response.CommunityScore;
import com.fraudgraph.ringdetector.entity.ClusterDecision;
import com.fraudgraph.ringdetector.service.CommunityDetectionService;
import com.fraudgraph.ringdetector.service.DecisionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.CLUSTERS)
public class ClusterController {

    private final CommunityDetectionService communityDetectionService;
    private final DecisionService decisionService;

    public ClusterController(CommunityDetectionService communityDetectionService,
                              DecisionService decisionService) {
        this.communityDetectionService = communityDetectionService;
        this.decisionService = decisionService;
    }

    @GetMapping
    public List<CommunityScore> clusters() {
        return communityDetectionService.currentClusters();
    }

    @PostMapping(ApiPaths.RECOMPUTE)
    public List<CommunityScore> recompute() {
        return communityDetectionService.recomputeClusters();
    }

    @GetMapping(ApiPaths.CLUSTER_GRAPH)
    public ClusterGraphResponse graph(@PathVariable long communityId) {
        return communityDetectionService.getClusterGraph(communityId);
    }

    @PostMapping(ApiPaths.CLUSTER_DECISION)
    public ClusterDecision decide(@PathVariable long communityId, @RequestBody DecisionRequest request,
                                  Authentication authentication) {
        // Who decided comes from the verified JWT, never from the request body.
        return decisionService.record(communityId, request, authentication.getName());
    }
}
