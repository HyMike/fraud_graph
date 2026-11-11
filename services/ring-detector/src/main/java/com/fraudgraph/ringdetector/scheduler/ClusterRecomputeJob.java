package com.fraudgraph.ringdetector.scheduler;

import com.fraudgraph.ringdetector.dto.response.CommunityScore;
import com.fraudgraph.ringdetector.service.CommunityDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.fraudgraph.ringdetector.constants.DetectionConstants.RECOMPUTE_INTERVAL_MS;

/**
 * Periodic Louvain/PageRank recompute. The other trigger is the manual
 * POST /api/clusters/recompute. Disabled in tests via scheduling.enabled=false
 * (see config/SchedulingConfig).
 */
@Component
public class ClusterRecomputeJob {

    private static final Logger log = LoggerFactory.getLogger(ClusterRecomputeJob.class);

    private final CommunityDetectionService communityDetectionService;

    public ClusterRecomputeJob(CommunityDetectionService communityDetectionService) {
        this.communityDetectionService = communityDetectionService;
    }

    @Scheduled(fixedDelay = RECOMPUTE_INTERVAL_MS)
    public void recompute() {
        List<CommunityScore> scores = communityDetectionService.recomputeClusters();
        log.info("Scheduled cluster recompute: {} communities scored", scores.size());
    }
}
