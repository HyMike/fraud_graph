import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Login } from './login';

const API = 'http://localhost:8081';

describe('Login', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
  });

  function submit(username: string, password: string) {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    (el.querySelector('input[name="username"]') as HTMLInputElement).value = username;
    (el.querySelector('input[name="password"]') as HTMLInputElement).value = password;
    (el.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    return { fixture, el };
  }

  it('signs in and goes to the dashboard', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    submit('lead', 'pw');

    const req = TestBed.inject(HttpTestingController).expectOne(`${API}/api/auth/login`);
    expect(req.request.body).toEqual({ username: 'lead', password: 'pw' });
    req.flush({ token: 't', username: 'lead', role: 'LEAD', expiresAt: '2099-01-01T00:00:00Z' });

    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('shows an error for wrong credentials', () => {
    const { fixture, el } = submit('lead', 'wrong');
    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/api/auth/login`)
      .flush('Invalid username or password', { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(el.querySelector('.error')?.textContent).toContain('Invalid username or password');
  });

  it('does not call the API with an empty form', () => {
    const { fixture, el } = submit('', '');
    TestBed.inject(HttpTestingController).expectNone(`${API}/api/auth/login`);
    fixture.detectChanges();
    expect(el.querySelector('.error')?.textContent).toContain('Enter your username and password');
  });
});
