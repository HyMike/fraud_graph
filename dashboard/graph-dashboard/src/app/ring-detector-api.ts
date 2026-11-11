import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Hardcoded rather than environment-based config: the browser always talks to
// the host-mapped port regardless of whether Angular is served via `ng serve`
// or from the dockerized nginx container — see infra/docker-compose.yml.
export const API_BASE_URL = 'http://localhost:8081';

export interface CommunityScore {
  communityId: number;
  clusterSize: number;
  density: number;
  sharedDeviceRatio: number;
  suspiciousnessScore: number;
  hubAccountId: string | null;
  hubPagerank: number;
  /** SHA-256 of the sorted member account IDs; communityId alone is renumbered on every recompute. */
  memberFingerprint: string;
}

export interface ClusterNode {
  id: string;
  type: 'Account' | 'Device';
  label: string | null;
  ringId: string | null;
  pagerank: number;
}

export interface ClusterEdge {
  source: string;
  target: string;
  relationshipType: 'TRANSFER' | 'USED';
}

export interface ClusterGraphResponse {
  communityId: number;
  nodes: ClusterNode[];
  edges: ClusterEdge[];
}

export type Decision = 'CONFIRM' | 'DISMISS';

/** Frozen copy of the cluster at decision time (null on rows recorded before it existed). */
export interface ClusterSnapshot {
  accounts: { id: string; label: string | null; ringId: string | null; pagerank: number | null }[];
  devices: { id: string; fingerprint: string | null }[];
  deviceUses: { accountId: string; deviceId: string }[];
  transfers: { id: string; fromAccountId: string; toAccountId: string; amount: number; ts: string }[];
}

export interface ClusterDecision {
  id: number;
  communityId: number;
  decision: Decision;
  reason: string;
  decidedBy: string;
  memberAccountIds: string[];
  memberFingerprint: string;
  clusterSnapshot: ClusterSnapshot | null;
  decidedAt: string;
}

@Injectable({ providedIn: 'root' })
export class RingDetectorApi {
  constructor(private readonly http: HttpClient) {}

  /** Scores from the last recompute — any role. */
  getClusters(): Observable<CommunityScore[]> {
    return this.http.get<CommunityScore[]>(`${API_BASE_URL}/api/clusters`);
  }

  /** Full GDS recompute — LEAD and ADMIN only. */
  recomputeClusters(): Observable<CommunityScore[]> {
    return this.http.post<CommunityScore[]>(`${API_BASE_URL}/api/clusters/recompute`, {});
  }

  getClusterGraph(communityId: number): Observable<ClusterGraphResponse> {
    return this.http.get<ClusterGraphResponse>(`${API_BASE_URL}/api/clusters/${communityId}/graph`);
  }

  /**
   * The fingerprint is the one the investigator was shown; the server answers
   * 409 Conflict if the community has been recomputed into a different set of
   * accounts since then. Who decided is taken from the login token server-side.
   */
  submitDecision(
    cluster: Pick<CommunityScore, 'communityId' | 'memberFingerprint'>,
    decision: Decision,
    reason: string,
  ): Observable<ClusterDecision> {
    return this.http.post<ClusterDecision>(`${API_BASE_URL}/api/clusters/${cluster.communityId}/decision`, {
      decision,
      reason,
      memberFingerprint: cluster.memberFingerprint,
    });
  }

  /** The audit log, newest first — AUDITOR, LEAD and ADMIN only. */
  getDecisions(): Observable<ClusterDecision[]> {
    return this.http.get<ClusterDecision[]>(`${API_BASE_URL}/api/decisions`);
  }
}
