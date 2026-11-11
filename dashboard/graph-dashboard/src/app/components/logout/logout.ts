import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth';

/** Visiting /logout discards the token and confirms it; there is no server call to make. */
@Component({
  selector: 'app-logout',
  imports: [RouterLink],
  template: `
    <main class="auth-card">
      <h2>Signed out</h2>
      <p>You have been signed out of the ring detector.</p>
      <a routerLink="/login">Sign in again</a>
    </main>
  `,
  styleUrl: '../login/login.scss',
})
export class Logout implements OnInit {
  constructor(private readonly auth: AuthService) {}

  ngOnInit(): void {
    this.auth.logout();
  }
}
