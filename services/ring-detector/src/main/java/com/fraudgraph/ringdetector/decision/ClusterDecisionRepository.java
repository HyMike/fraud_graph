package com.fraudgraph.ringdetector.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusterDecisionRepository extends JpaRepository<ClusterDecision, Long> {

    List<ClusterDecision> findByCommunityIdOrderByDecidedAtDesc(long communityId);

    List<ClusterDecision> findAllByOrderByDecidedAtDesc();
}
