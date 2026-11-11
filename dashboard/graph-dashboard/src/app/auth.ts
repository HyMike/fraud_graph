import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { API_BASE_URL } from './ring-detector-api';

export type Role = 'ANALYST' | 'LEAD' | 'AUDITOR' | 'ADMIN';

export interface Session {
  token: string;
  username: string;
  role: Role;
  expiresAt: string;
}

/**
 * Holds the JWT from POST /api/auth/login in memory only — never localStorage,
 * so a script injected into the page cannot lift it from storage. The cost is
 * that a page reload signs the user out. Logging out just discards the token;
 * the server has no session to end, and the token expires on its own shortly.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly session = signal<Session | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;
  // setTimeout fires immediately for delays above 2^31-1 ms (~24.8 days).
  private static readonly MAX_TIMEOUT_MS = 2_147_483_647;

  readonly current = this.session.asReadonly();
  readonly isLoggedIn = computed(() => this.session() !== null);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  login(username: string, password: string): Observable<Session> {
    return this.http
      .post<Session>(`${API_BASE_URL}/api/auth/login`, { username, password })
      .pipe(tap((session) => this.start(session)));
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.session()?.role;
    return role !== undefined && roles.includes(role);
  }

  /** Clears the session; navigates to the login page with a reason if one is given. */
  logout(reason?: 'expired'): void {
    clearTimeout(this.expiryTimer);
    this.session.set(null);
    if (reason) {
      this.router.navigate(['/login'], { queryParams: { reason } });
    }
  }

  private start(session: Session): void {
    clearTimeout(this.expiryTimer);
    this.session.set(session);
    const msLeft = new Date(session.expiresAt).getTime() - Date.now();
    this.expiryTimer = setTimeout(
      () => this.logout('expired'),
      Math.min(Math.max(msLeft, 0), AuthService.MAX_TIMEOUT_MS),
    );
  }
}
