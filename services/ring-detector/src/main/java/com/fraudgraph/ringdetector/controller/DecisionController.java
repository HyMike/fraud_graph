package com.fraudgraph.ringdetector.controller;

import com.fraudgraph.ringdetector.constants.ApiPaths;
import com.fraudgraph.ringdetector.entity.ClusterDecision;
import com.fraudgraph.ringdetector.repository.ClusterDecisionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only view of the audit log, newest first (AUDITOR, LEAD, ADMIN — see SecurityConfig). */
@RestController
@RequestMapping(ApiPaths.DECISIONS)
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
