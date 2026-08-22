import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

// Hardcoded rather than environment-based config: the browser always talks to
// the host-mapped port regardless of whether Angular is served via `ng serve`
// or from the dockerized nginx container — see infra/docker-compose.yml.
const API_BASE_URL = 'http://localhost:8081';

export interface CommunityScore {
  communityId: number;
  clusterSize: number;
  density: number;
  sharedDeviceRatio: number;
  suspiciousnessScore: number;
  hubAccountId: string | null;
  hubPagerank: number;
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

export interface ClusterDecision {
  id: number;
  communityId: number;
  decision: Decision;
  reason: string;
  decidedAt: string;
}

@Injectable({ providedIn: 'root' })
export class RingDetectorApi {
  constructor(private readonly http: HttpClient) {}

  recomputeClusters(): Observable<CommunityScore[]> {
    return this.http.post<CommunityScore[]>(`${API_BASE_URL}/api/clusters/recompute`, {});
  }

  getClusterGraph(communityId: number): Observable<ClusterGraphResponse> {
    return this.http.get<ClusterGraphResponse>(`${API_BASE_URL}/api/clusters/${communityId}/graph`);
  }

  submitDecision(communityId: number, decision: Decision, reason: string): Observable<ClusterDecision> {
    return this.http.post<ClusterDecision>(`${API_BASE_URL}/api/clusters/${communityId}/decision`, {
      decision,
      reason,
    });
  }
}
