package com.fraudgraph.ringdetector.enums;

/**
 * Dashboard roles. Each maps to a Spring authority ROLE_&lt;name&gt; (see SecurityConfig
 * for which endpoints each may call):
 *   ANALYST — view clusters/detections, record Confirm/Dismiss decisions
 *   LEAD    — ANALYST + trigger a full GDS recompute, read the audit log
 *   AUDITOR — read-only: view clusters/detections and the audit log, no writes
 *   ADMIN   — superuser: everything above
 */
public enum Role {
    ANALYST, LEAD, AUDITOR, ADMIN
}
