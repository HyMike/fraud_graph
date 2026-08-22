package com.fraudgraph.ringdetector.decision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * An investigator's Confirm/Dismiss decision on a flagged Louvain community
 * (Phase 4 audit log). The only place Postgres is used in this project — see
 * CLAUDE.md: everything else lives in Neo4j.
 */
@Entity
@Table(name = "cluster_decisions")
public class ClusterDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private long communityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant decidedAt;

    protected ClusterDecision() {
        // JPA
    }

    public ClusterDecision(long communityId, Decision decision, String reason) {
        this.communityId = communityId;
        this.decision = decision;
        this.reason = reason;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public long getCommunityId() {
        return communityId;
    }

    public Decision getDecision() {
        return decision;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public enum Decision {
        CONFIRM, DISMISS
    }
}
