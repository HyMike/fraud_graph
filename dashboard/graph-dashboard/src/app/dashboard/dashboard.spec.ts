import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { vi } from 'vitest';
import { AuthService, Role } from '../auth';
import { CommunityScore } from '../ring-detector-api';
import { Dashboard } from './dashboard';

// Cytoscape renders to a canvas, which jsdom doesn't implement — mock it here
// the same way fraud_pipeline's case-dashboard mocks Chart.js for the same reason.
vi.mock('cytoscape', () => ({
  default: vi.fn(() => ({ destroy: vi.fn() })),
}));

const API = 'http://localhost:8081';

const cluster: CommunityScore = {
  communityId: 425,
  clusterSize: 16,
  density: 0.12,
  sharedDeviceRatio: 0,
  suspiciousnessScore: 0.06,
  hubAccountId: 'acct_0401',
  hubPagerank: 3.2,
  memberFingerprint: 'abc123',
};

// Reaches past `protected` for test access, as is common for component state.
type DashboardState = {
  error(): string | null;
  decide(c: CommunityScore, d: 'CONFIRM' | 'DISMISS', reason: string): void;
  decisionFor(c: CommunityScore): string | undefined;
};

function setUp(role: Role) {
  TestBed.configureTestingModule({
    imports: [Dashboard],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { hasRole: (...roles: Role[]) => roles.includes(role) } },
    ],
  });
  const fixture = TestBed.createComponent(Dashboard);
  const http = TestBed.inject(HttpTestingController);
  fixture.detectChanges(); // ngOnInit
  http.expectOne(`${API}/api/clusters`).flush([cluster]);
  fixture.detectChanges();
  return {
    fixture,
    http,
    app: fixture.componentInstance as unknown as DashboardState,
    el: fixture.nativeElement as HTMLElement,
  };
}

describe('Dashboard', () => {
  describe('as an ANALYST', () => {
    it('loads the current clusters without recomputing and hides recompute and the audit log', () => {
      const { http, el } = setUp('ANALYST');

      http.expectNone(`${API}/api/clusters/recompute`);
      http.expectNone(`${API}/api/decisions`);
      expect(el.textContent).not.toContain('Recompute clusters now');
      expect(el.textContent).not.toContain('Audit log');
      expect(el.querySelectorAll('tbody tr').length).toBe(1);
    });

    it('sends the decision and fingerprint, but not who decided (the server takes that from the token)', () => {
      const { http, app } = setUp('ANALYST');

      app.decide(cluster, 'CONFIRM', 'mule fan-in');

      const req = http.expectOne(`${API}/api/clusters/425/decision`);
      expect(req.request.body).toEqual({
        decision: 'CONFIRM',
        reason: 'mule fan-in',
        memberFingerprint: 'abc123',
      });
      req.flush({});
      expect(app.decisionFor(cluster)).toBe('CONFIRM');
    });

    it('does not submit without a reason', () => {
      const { http, app } = setUp('ANALYST');

      app.decide(cluster, 'CONFIRM', '  ');

      http.expectNone(`${API}/api/clusters/425/decision`);
      expect(app.error()).toContain('reason');
    });

    it('tells the investigator to reload when the cluster changed (409)', () => {
      const { http, app } = setUp('ANALYST');

      app.decide(cluster, 'DISMISS', 'family');
      http
        .expectOne(`${API}/api/clusters/425/decision`)
        .flush('changed', { status: 409, statusText: 'Conflict' });

      expect(app.error()).toContain('has changed');
      expect(app.decisionFor(cluster)).toBeUndefined();
    });
  });

  describe('as an AUDITOR', () => {
    it('shows the audit log read-only, with no decision or recompute controls', () => {
      const { fixture, http, el } = setUp('AUDITOR');

      http.expectOne(`${API}/api/decisions`).flush([
        {
          id: 1,
          communityId: 425,
          decision: 'CONFIRM',
          reason: 'mule fan-in',
          decidedBy: 'alice',
          memberAccountIds: ['acct_0401', 'acct_0402'],
          memberFingerprint: 'abc123',
          clusterSnapshot: { accounts: [], devices: [], deviceUses: [], transfers: [{}, {}, {}] },
          decidedAt: '2026-11-11T18:00:00Z',
        },
      ]);
      fixture.detectChanges();

      expect(el.textContent).toContain('Audit log');
      expect(el.querySelector('.audit-log')?.textContent).toContain('alice');
      expect(el.textContent).toContain('Read-only');
      expect(el.textContent).not.toContain('Recompute clusters now');
      expect(el.querySelector('input[placeholder^="Reason"]')).toBeNull();
    });
  });

  describe('as a LEAD', () => {
    it('can recompute, decide, and read the audit log', () => {
      const { fixture, http, el } = setUp('LEAD');
      http.expectOne(`${API}/api/decisions`).flush([]);
      fixture.detectChanges();

      expect(el.textContent).toContain('Recompute clusters now');
      expect(el.querySelector('input[placeholder^="Reason"]')).not.toBeNull();
      expect(el.textContent).toContain('Audit log');
    });
  });
});
