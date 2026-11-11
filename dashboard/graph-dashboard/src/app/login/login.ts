import { Component, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../auth';

@Component({
  selector: 'app-login',
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnInit {
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    if (this.route.snapshot.queryParamMap.get('reason') === 'expired') {
      this.notice.set('Your session expired. Sign in again to continue.');
    }
  }

  submit(username: string, password: string): void {
    if (!username.trim() || !password) {
      this.error.set('Enter your username and password.');
      return;
    }
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(username.trim(), password).subscribe({
      next: () => this.router.navigate(['/']),
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(
          err.status === 401
            ? 'Invalid username or password.'
            : 'Could not reach ring-detector. Is it running on localhost:8081?',
        );
      },
    });
  }
}
