package com.fraudgraph.ringdetector.service;

import com.fraudgraph.ringdetector.dto.response.CounterpartyFlag;
import com.fraudgraph.ringdetector.dto.response.CycleFlag;
import com.fraudgraph.ringdetector.repository.RingPatternRepository;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.fraudgraph.ringdetector.constants.DetectionConstants.MAX_CYCLE_LENGTH;
import static com.fraudgraph.ringdetector.constants.DetectionConstants.MIN_CYCLE_LENGTH;

/**
 * Phase 2 structural pattern detection: fan-in, fan-out, and bounded-length cycles.
 * Local-subgraph Cypher only, no GDS — this is the near-real-time fast path
 * (see CLAUDE.md); community detection is Phase 3.
 */
@Service
public class RingDetectionService {

    private final RingPatternRepository ringPatternRepository;

    public RingDetectionService(RingPatternRepository ringPatternRepository) {
        this.ringPatternRepository = ringPatternRepository;
    }

    public List<CounterpartyFlag> detectFanIn(long minDistinctSenders, long windowHours) {
        return ringPatternRepository.findFanIn(minDistinctSenders, windowHours);
    }

    public List<CounterpartyFlag> detectFanOut(long minDistinctReceivers, long windowHours) {
        return ringPatternRepository.findFanOut(minDistinctReceivers, windowHours);
    }

    public List<CycleFlag> detectCycles(int maxLength, int limit) {
        // Validated here because the repository interpolates maxLength into the Cypher text.
        if (maxLength < MIN_CYCLE_LENGTH || maxLength > MAX_CYCLE_LENGTH) {
            throw new IllegalArgumentException(
                "maxLength must be between %d and %d".formatted(MIN_CYCLE_LENGTH, MAX_CYCLE_LENGTH));
        }
        return ringPatternRepository.findCycles(maxLength, limit);
    }
}
