import { Role } from '../models/auth';

// Which roles see which controls. These mirror the access rules in ring-detector's
// SecurityConfig; the API enforces them regardless — these only hide buttons a
// user could not use anyway.
export const RECOMPUTE_ROLES: readonly Role[] = ['LEAD', 'ADMIN'];
export const DECIDE_ROLES: readonly Role[] = ['ANALYST', 'LEAD', 'ADMIN'];
export const AUDIT_LOG_ROLES: readonly Role[] = ['AUDITOR', 'LEAD', 'ADMIN'];
