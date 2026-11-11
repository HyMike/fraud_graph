package com.fraudgraph.ringdetector.repository;

import com.fraudgraph.ringdetector.entity.ClusterDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusterDecisionRepository extends JpaRepository<ClusterDecision, Long> {

    List<ClusterDecision> findByCommunityIdOrderByDecidedAtDesc(long communityId);

    List<ClusterDecision> findAllByOrderByDecidedAtDesc();
}
