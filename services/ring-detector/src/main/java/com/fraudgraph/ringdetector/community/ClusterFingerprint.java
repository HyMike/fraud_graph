package com.fraudgraph.ringdetector.community;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;

/**
 * A stable identity for a cluster's membership. Louvain community IDs are
 * renumbered on every recompute, so "community 425" means nothing over time;
 * the SHA-256 of the sorted member account IDs does. The dashboard echoes the
 * fingerprint back with a decision so the server can tell whether the cluster
 * the investigator looked at is still the cluster behind that community ID.
 */
public final class ClusterFingerprint {

    private ClusterFingerprint() {
    }

    public static String of(Collection<String> accountIds) {
        String canonical = String.join("\n", accountIds.stream().sorted().toList());
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }
}
