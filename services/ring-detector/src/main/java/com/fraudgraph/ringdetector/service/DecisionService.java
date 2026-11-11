package com.fraudgraph.ringdetector.service;

import com.fraudgraph.ringdetector.dto.request.DecisionRequest;
import com.fraudgraph.ringdetector.dto.response.ClusterSnapshot;
import com.fraudgraph.ringdetector.entity.ClusterDecision;
import com.fraudgraph.ringdetector.exception.ClusterChangedException;
import com.fraudgraph.ringdetector.repository.ClusterDecisionRepository;
import com.fraudgraph.ringdetector.util.ClusterFingerprint;
import org.springframework.stereotype.Service;

/**
 * Records an investigator decision together with a snapshot of the cluster it
 * applies to (accounts, devices, transfers). The snapshot is taken from Neo4j at
 * decision time and only accepted if its accounts match the fingerprint the
 * investigator was shown, so the audit row can never point at a different group
 * of accounts than the one that was reviewed.
 */
@Service
public class DecisionService {

    private final CommunityDetectionService communityDetectionService;
    private final ClusterDecisionRepository clusterDecisionRepository;

    public DecisionService(CommunityDetectionService communityDetectionService,
                           ClusterDecisionRepository clusterDecisionRepository) {
        this.communityDetectionService = communityDetectionService;
        this.clusterDecisionRepository = clusterDecisionRepository;
    }

    public ClusterDecision record(long communityId, DecisionRequest request, String decidedBy) {
        if (request.decision() == null) {
            throw new IllegalArgumentException("decision is required");
        }
        if (isBlank(request.reason())) {
            throw new IllegalArgumentException("reason is required");
        }
        if (isBlank(decidedBy)) {
            throw new IllegalStateException("decision recorded without an authenticated user");
        }
        if (isBlank(request.memberFingerprint())) {
            throw new IllegalArgumentException("memberFingerprint is required");
        }

        ClusterSnapshot snapshot = communityDetectionService.getClusterSnapshot(communityId);
        if (snapshot.accounts().isEmpty()
            || !ClusterFingerprint.of(snapshot.accountIds()).equals(request.memberFingerprint())) {
            throw new ClusterChangedException(communityId);
        }

        return clusterDecisionRepository.save(new ClusterDecision(
            communityId,
            request.decision(),
            request.reason().strip(),
            decidedBy,
            snapshot,
            request.memberFingerprint()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
