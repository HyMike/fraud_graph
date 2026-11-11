package com.fraudgraph.ringdetector.community;

import com.fraudgraph.ringdetector.decision.ClusterDecision;
import com.fraudgraph.ringdetector.decision.DecisionRequest;
import com.fraudgraph.ringdetector.decision.DecisionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
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

    @PostMapping("/recompute")
    public List<CommunityScore> recompute() {
        return communityDetectionService.recomputeClusters();
    }

    @GetMapping("/{communityId}/graph")
    public ClusterGraphResponse graph(@PathVariable long communityId) {
        return communityDetectionService.getClusterGraph(communityId);
    }

    @PostMapping("/{communityId}/decision")
    public ClusterDecision decide(@PathVariable long communityId, @RequestBody DecisionRequest request,
                                  Authentication authentication) {
        // Who decided comes from the verified JWT, never from the request body.
        return decisionService.record(communityId, request, authentication.getName());
    }
}
