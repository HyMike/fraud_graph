import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { API_UNREACHABLE_MESSAGE } from '../../constants/messages';
import { AUDIT_LOG_ROLES, DECIDE_ROLES, RECOMPUTE_ROLES } from '../../constants/roles';
import { ClusterGraphResponse, CommunityScore } from '../../models/cluster';
import { ClusterDecision, Decision } from '../../models/decision';
import { AuthService } from '../../services/auth';
import { RingDetectorApi } from '../../services/ring-detector-api';
import { ClusterGraph } from '../cluster-graph/cluster-graph';

/**
 * The investigator view. What it shows follows the signed-in role (see
 * constants/roles.ts; the API enforces the same rules — hiding a button here
 * is only a convenience).
 */
@Component({
  selector: 'app-dashboard',
  imports: [ClusterGraph],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  protected readonly clusters = signal<CommunityScore[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedCommunityId = signal<number | null>(null);
  protected readonly selectedGraph = signal<ClusterGraphResponse | null>(null);
  protected readonly auditLog = signal<ClusterDecision[]>([]);
  // Keyed by member fingerprint, not communityId: Louvain renumbers community IDs
  // on every recompute, so an ID-keyed badge could land on a different cluster.
  protected readonly decided = signal<Map<string, Decision>>(new Map());

  protected readonly canRecompute: boolean;
  protected readonly canDecide: boolean;
  protected readonly canReadAuditLog: boolean;

  constructor(
    private readonly api: RingDetectorApi,
    auth: AuthService,
  ) {
    this.canRecompute = auth.hasRole(...RECOMPUTE_ROLES);
    this.canDecide = auth.hasRole(...DECIDE_ROLES);
    this.canReadAuditLog = auth.hasRole(...AUDIT_LOG_ROLES);
  }

  ngOnInit(): void {
    this.loading.set(true);
    this.api.getClusters().subscribe({
      next: (scores) => this.showClusters(scores),
      error: (err: HttpErrorResponse) => this.fail(err),
    });
    this.loadAuditLog();
  }

  recompute(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.recomputeClusters().subscribe({
      next: (scores) => this.showClusters(scores),
      error: (err: HttpErrorResponse) => this.fail(err),
    });
  }

  select(communityId: number): void {
    this.selectedCommunityId.set(communityId);
    this.selectedGraph.set(null);
    this.api.getClusterGraph(communityId).subscribe((graph) => this.selectedGraph.set(graph));
  }

  decide(cluster: CommunityScore, decision: Decision, reason: string): void {
    if (!reason.trim()) {
      this.error.set('Enter a reason before confirming or dismissing a cluster.');
      return;
    }
    this.error.set(null);
    this.api.submitDecision(cluster, decision, reason).subscribe({
      next: () => {
        const next = new Map(this.decided());
        next.set(cluster.memberFingerprint, decision);
        this.decided.set(next);
        this.loadAuditLog();
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.error.set(
            `Cluster ${cluster.communityId} has changed since this list was loaded. Reload and review it again.`,
          );
        } else {
          this.fail(err);
        }
      },
    });
  }

  decisionFor(cluster: CommunityScore): Decision | undefined {
    return this.decided().get(cluster.memberFingerprint);
  }

  private loadAuditLog(): void {
    if (this.canReadAuditLog) {
      this.api.getDecisions().subscribe({
        next: (decisions) => this.auditLog.set(decisions),
        error: (err: HttpErrorResponse) => this.fail(err),
      });
    }
  }

  private showClusters(scores: CommunityScore[]): void {
    this.clusters.set(scores);
    this.loading.set(false);
  }

  private fail(err: HttpErrorResponse): void {
    this.loading.set(false);
    // 401 is handled by the interceptor (back to the login page).
    if (err.status === 403) {
      this.error.set('Your role is not allowed to do that.');
    } else if (err.status !== 401) {
      this.error.set(API_UNREACHABLE_MESSAGE);
    }
  }
}
