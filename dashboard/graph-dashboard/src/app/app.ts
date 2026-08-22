import { Component, signal } from '@angular/core';
import { ClusterGraph } from './cluster-graph/cluster-graph';
import { ClusterGraphResponse, CommunityScore, Decision, RingDetectorApi } from './ring-detector-api';

@Component({
  selector: 'app-root',
  imports: [ClusterGraph],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly title = signal('graph-dashboard');

  protected readonly clusters = signal<CommunityScore[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly selectedCommunityId = signal<number | null>(null);
  protected readonly selectedGraph = signal<ClusterGraphResponse | null>(null);
  protected readonly decided = signal<Map<number, Decision>>(new Map());

  constructor(private readonly api: RingDetectorApi) {}

  recompute(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.recomputeClusters().subscribe({
      next: (scores) => {
        this.clusters.set(scores);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not reach ring-detector. Is it running on localhost:8081?');
        this.loading.set(false);
      },
    });
  }

  select(communityId: number): void {
    this.selectedCommunityId.set(communityId);
    this.selectedGraph.set(null);
    this.api.getClusterGraph(communityId).subscribe((graph) => this.selectedGraph.set(graph));
  }

  decide(communityId: number, decision: Decision, reason: string): void {
    if (!reason.trim()) {
      return;
    }
    this.api.submitDecision(communityId, decision, reason).subscribe(() => {
      const next = new Map(this.decided());
      next.set(communityId, decision);
      this.decided.set(next);
    });
  }

  decisionFor(communityId: number): Decision | undefined {
    return this.decided().get(communityId);
  }
}
