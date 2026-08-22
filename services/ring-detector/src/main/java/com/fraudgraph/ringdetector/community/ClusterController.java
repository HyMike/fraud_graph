package com.fraudgraph.ringdetector.community;

import com.fraudgraph.ringdetector.decision.ClusterDecision;
import com.fraudgraph.ringdetector.decision.ClusterDecisionRepository;
import com.fraudgraph.ringdetector.decision.DecisionRequest;
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
    private final ClusterDecisionRepository clusterDecisionRepository;

    public ClusterController(CommunityDetectionService communityDetectionService,
                              ClusterDecisionRepository clusterDecisionRepository) {
        this.communityDetectionService = communityDetectionService;
        this.clusterDecisionRepository = clusterDecisionRepository;
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
    public ClusterDecision decide(@PathVariable long communityId, @RequestBody DecisionRequest request) {
        if (request.decision() == null) {
            throw new IllegalArgumentException("decision is required");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        return clusterDecisionRepository.save(new ClusterDecision(communityId, request.decision(), request.reason()));
    }
}
