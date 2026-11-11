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

/** Frozen copy of the cluster at decision time (null on rows recorded before it existed). */
export interface ClusterSnapshot {
  accounts: { id: string; label: string | null; ringId: string | null; pagerank: number | null }[];
  devices: { id: string; fingerprint: string | null }[];
  deviceUses: { accountId: string; deviceId: string }[];
  transfers: { id: string; fromAccountId: string; toAccountId: string; amount: number; ts: string }[];
}
