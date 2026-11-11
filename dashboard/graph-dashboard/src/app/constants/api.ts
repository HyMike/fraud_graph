/**
 * ring-detector REST paths, relative to environment.apiBaseUrl. Mirrors the
 * backend's ApiPaths.java (services/ring-detector/.../constants); keep the two in sync.
 */
export const API_PATHS = {
  login: '/api/auth/login',
  clusters: '/api/clusters',
  recompute: '/api/clusters/recompute',
  clusterGraph: (communityId: number) => `/api/clusters/${communityId}/graph`,
  clusterDecision: (communityId: number) => `/api/clusters/${communityId}/decision`,
  decisions: '/api/decisions',
} as const;
