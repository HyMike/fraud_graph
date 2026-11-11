import { ClusterSnapshot } from './cluster';

export type Decision = 'CONFIRM' | 'DISMISS';

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
