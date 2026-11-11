package com.fraudgraph.ringdetector.entity;

import com.fraudgraph.ringdetector.dto.response.ClusterSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * An investigator's Confirm/Dismiss decision on a flagged Louvain community
 * (Phase 4 audit log). The only place Postgres is used in this project — see
 * CLAUDE.md: everything else lives in Neo4j.
 *
 * communityId is only meaningful at the moment of the decision (Louvain
 * renumbers communities on every recompute), so the row also snapshots the
 * exact member account IDs and their fingerprint — that is what the
 * investigator actually confirmed or dismissed. clusterSnapshot (JSONB) extends
 * that to the full graph: devices, device usage and every internal TRANSFER
 * with amount and timestamp. It is null on rows recorded before V4.
 *
 * decidedBy is the authenticated username from the JWT.
 *
 * Schema is owned by Flyway (src/main/resources/db/migration), not Hibernate.
 * @Immutable: an audit row is written once and never updated.
 */
@Entity
@Immutable
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
    private String decidedBy;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> memberAccountIds;

    @Column(nullable = false, length = 64)
    private String memberFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private ClusterSnapshot clusterSnapshot;

    @Column(nullable = false)
    private Instant decidedAt;

    protected ClusterDecision() {
        // JPA
    }

    public ClusterDecision(long communityId, Decision decision, String reason, String decidedBy,
                           ClusterSnapshot clusterSnapshot, String memberFingerprint) {
        this.communityId = communityId;
        this.decision = decision;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.memberAccountIds = clusterSnapshot.accountIds();
        this.clusterSnapshot = clusterSnapshot;
        this.memberFingerprint = memberFingerprint;
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

    public String getDecidedBy() {
        return decidedBy;
    }

    public List<String> getMemberAccountIds() {
        return memberAccountIds;
    }

    public String getMemberFingerprint() {
        return memberFingerprint;
    }

    public ClusterSnapshot getClusterSnapshot() {
        return clusterSnapshot;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public enum Decision {
        CONFIRM, DISMISS
    }
}
