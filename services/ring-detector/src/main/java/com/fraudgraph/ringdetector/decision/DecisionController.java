package com.fraudgraph.ringdetector.decision;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of the audit log, newest first (AUDITOR, LEAD, ADMIN — see SecurityConfig). */
@RestController
@RequestMapping("/api/decisions")
public class DecisionController {

    private final ClusterDecisionRepository clusterDecisionRepository;

    public DecisionController(ClusterDecisionRepository clusterDecisionRepository) {
        this.clusterDecisionRepository = clusterDecisionRepository;
    }

    @GetMapping
    public List<ClusterDecision> decisions() {
        return clusterDecisionRepository.findAllByOrderByDecidedAtDesc();
    }
}
