package com.fraudgraph.ringdetector.community;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    private final CommunityDetectionService communityDetectionService;

    public ClusterController(CommunityDetectionService communityDetectionService) {
        this.communityDetectionService = communityDetectionService;
    }

    @PostMapping("/recompute")
    public List<CommunityScore> recompute() {
        return communityDetectionService.recomputeClusters();
    }
}
