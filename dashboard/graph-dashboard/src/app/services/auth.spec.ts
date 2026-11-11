import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { AuthService } from './auth';
import { authInterceptor } from '../interceptors/auth-interceptor';

const API = 'http://localhost:8081';

describe('AuthService + authInterceptor', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  function logIn(role = 'ANALYST') {
    auth.login('analyst', 'pw').subscribe();
    http.expectOne(`${API}/api/auth/login`).flush({
      token: 'jwt-123',
      username: 'analyst',
      role,
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
    });
  }

  it('keeps the session after login and reports the role', () => {
    logIn('LEAD');
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.hasRole('LEAD', 'ADMIN')).toBe(true);
    expect(auth.hasRole('AUDITOR')).toBe(false);
  });

  it('attaches the Bearer token to ring-detector calls only', () => {
    logIn();
    client.get(`${API}/api/clusters`).subscribe();
    client.get('https://example.com/other').subscribe();

    expect(http.expectOne(`${API}/api/clusters`).request.headers.get('Authorization')).toBe('Bearer jwt-123');
    expect(http.expectOne('https://example.com/other').request.headers.has('Authorization')).toBe(false);
  });

  it('sends no Authorization header before login', () => {
    client.get(`${API}/api/clusters`).subscribe({ error: () => {} });
    expect(http.expectOne(`${API}/api/clusters`).request.headers.has('Authorization')).toBe(false);
  });

  it('drops the session and goes to the login page when the API answers 401', () => {
    logIn();
    client.get(`${API}/api/clusters`).subscribe({ error: () => {} });
    http.expectOne(`${API}/api/clusters`).flush('', { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { reason: 'expired' } });
  });

  it('logout discards the token', () => {
    logIn();
    auth.logout();
    expect(auth.token()).toBeNull();
    expect(auth.isLoggedIn()).toBe(false);
  });

  it('signs out automatically when the token expires', () => {
    vi.useFakeTimers();
    try {
      logIn();
      vi.advanceTimersByTime(61_000);
      expect(auth.isLoggedIn()).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });
});
