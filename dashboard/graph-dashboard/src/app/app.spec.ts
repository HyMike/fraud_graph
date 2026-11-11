import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { App } from './app';
import { Session } from './models/auth';
import { AuthService } from './services/auth';

describe('App shell', () => {
  const session = signal<Session | null>(null);

  beforeEach(async () => {
    session.set(null);
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { current: session } },
      ],
    }).compileComponents();
  });

  it('renders the title', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toContain('fraud-graph');
  });

  it('shows no user or sign-out link when signed out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Sign out');
  });

  it('shows the signed-in user, their role, and a sign-out link', async () => {
    session.set({ token: 't', username: 'lead', role: 'LEAD', expiresAt: '2099-01-01T00:00:00Z' });
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.session')?.textContent).toContain('lead');
    expect(el.querySelector('.role-badge')?.textContent).toContain('LEAD');
    expect(el.querySelector('a[href="/logout"]')).not.toBeNull();
  });
});
