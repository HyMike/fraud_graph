package com.fraudgraph.ringdetector.constants;

/**
 * Every REST path the API exposes. Controllers map these and SecurityConfig
 * guards the same constants, so a renamed endpoint cannot silently drop out
 * of its access rule. The dashboard keeps its own copy of these paths
 * (graph-dashboard/src/app/constants/api.ts); keep the two in sync.
 */
public final class ApiPaths {

    public static final String API_ROOT = "/api";
    public static final String ALL_API = API_ROOT + "/**";

    // Controller base paths (@RequestMapping)
    public static final String AUTH = API_ROOT + "/auth";
    public static final String CLUSTERS = API_ROOT + "/clusters";
    public static final String DECISIONS = API_ROOT + "/decisions";
    public static final String DETECTION = API_ROOT + "/detection";

    // Endpoint paths, relative to their controller's base path
    public static final String LOGIN = "/login";
    public static final String RECOMPUTE = "/recompute";
    public static final String CLUSTER_GRAPH = "/{communityId}/graph";
    public static final String CLUSTER_DECISION = "/{communityId}/decision";
    public static final String FAN_IN = "/fan-in";
    public static final String FAN_OUT = "/fan-out";
    public static final String CYCLES = "/cycles";

    private ApiPaths() {
    }
}
