export type Role = 'ANALYST' | 'LEAD' | 'AUDITOR' | 'ADMIN';

/** The body of a successful POST /api/auth/login. */
export interface Session {
  token: string;
  username: string;
  role: Role;
  expiresAt: string;
}
