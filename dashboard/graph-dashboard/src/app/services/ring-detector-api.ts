import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_PATHS } from '../constants/api';
import { ClusterGraphResponse, CommunityScore } from '../models/cluster';
import { ClusterDecision, Decision } from '../models/decision';

@Injectable({ providedIn: 'root' })
export class RingDetectorApi {
  constructor(private readonly http: HttpClient) {}

  /** Scores from the last recompute — any role. */
  getClusters(): Observable<CommunityScore[]> {
    return this.http.get<CommunityScore[]>(`${environment.apiBaseUrl}${API_PATHS.clusters}`);
  }

  /** Full GDS recompute — LEAD and ADMIN only. */
  recomputeClusters(): Observable<CommunityScore[]> {
    return this.http.post<CommunityScore[]>(`${environment.apiBaseUrl}${API_PATHS.recompute}`, {});
  }

  getClusterGraph(communityId: number): Observable<ClusterGraphResponse> {
    return this.http.get<ClusterGraphResponse>(`${environment.apiBaseUrl}${API_PATHS.clusterGraph(communityId)}`);
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
    return this.http.post<ClusterDecision>(`${environment.apiBaseUrl}${API_PATHS.clusterDecision(cluster.communityId)}`, {
      decision,
      reason,
      memberFingerprint: cluster.memberFingerprint,
    });
  }

  /** The audit log, newest first — AUDITOR, LEAD and ADMIN only. */
  getDecisions(): Observable<ClusterDecision[]> {
    return this.http.get<ClusterDecision[]>(`${environment.apiBaseUrl}${API_PATHS.decisions}`);
  }
}
