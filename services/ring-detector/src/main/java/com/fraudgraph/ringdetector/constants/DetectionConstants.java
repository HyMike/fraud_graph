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

    // Suspiciousness score weights. Density and shared-device signal are weighted
    // equally since either alone can indicate a ring; cluster size contributes a
    // smooth, small bonus toward tighter (smaller) clusters without a hard cutoff,
    // since normal traffic tends to form large, sprawling communities and rings
    // tend to be small and self-contained.
    public static final double DENSITY_WEIGHT = 0.4;
    public static final double SHARED_DEVICE_WEIGHT = 0.4;
    public static final double SIZE_WEIGHT = 0.2;

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
