package com.fraudgraph.ringdetector.constants;

/**
 * Fixed parameters of the detection algorithms. These are hand-picked, not
 * trained or fit (see CLAUDE.md: no ML model anywhere in this build).
 */
public final class DetectionConstants {

    // ---- Community detection (Louvain + PageRank via GDS) ----

    /** Name of the in-memory GDS graph projection used during a recompute. */
    public static final String GDS_GRAPH_NAME = "ringDetectorGraph";

    /**
     * Placeholder cadence of the scheduled recompute for the MVP/demo; a
     * production interval would be tuned operationally.
     */
    public static final long RECOMPUTE_INTERVAL_MS = 5 * 60 * 1000;

    // Suspiciousness score = ringShape × openingBurst (see CommunityDetectionService).

    /** A single account can't be a ring, so smaller communities aren't scored or listed. */
    public static final int MIN_RING_SIZE = 2;

    /**
     * Scale of the opening-burst term 1 / (1 + openingSpanDays / scale): accounts all
     * opened on one day score 1.0, a 30-day spread 0.5, a year's spread under 0.1.
     * Coordinated rings open their accounts in a batch; families and businesses
     * accumulate theirs over months or years.
     */
    public static final double OPENING_BURST_SCALE_DAYS = 30.0;

    // ---- Structural detection (fan-in, fan-out, cycles) ----

    /** Allowed range for a cycle query's maxLength; bounds keep the path match cheap. */
    public static final int MIN_CYCLE_LENGTH = 2;
    public static final int MAX_CYCLE_LENGTH = 6;

    // Request parameter defaults for /api/detection/* (strings, as @RequestParam requires).
    public static final String DEFAULT_MIN_DISTINCT_COUNTERPARTIES = "10";
    public static final String DEFAULT_WINDOW_HOURS = "24";
    public static final String DEFAULT_CYCLE_MAX_LENGTH = "4";
    public static final String DEFAULT_CYCLE_LIMIT = "25";

    private DetectionConstants() {
    }
}
