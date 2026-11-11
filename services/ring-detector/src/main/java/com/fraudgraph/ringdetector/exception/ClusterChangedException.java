package com.fraudgraph.ringdetector.exception;

/**
 * The community ID no longer refers to the set of accounts the investigator
 * reviewed (a recompute ran in between). Mapped to 409 Conflict.
 */
public class ClusterChangedException extends RuntimeException {

    public ClusterChangedException(long communityId) {
        super("Cluster %d has changed since it was loaded (clusters were recomputed). Reload and review it again."
            .formatted(communityId));
    }
}
