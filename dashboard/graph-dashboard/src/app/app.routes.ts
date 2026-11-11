import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';
import { Dashboard } from './dashboard/dashboard';
import { Login } from './login/login';
import { Logout } from './logout/logout';

export const routes: Routes = [
  { path: '', component: Dashboard, canActivate: [authGuard] },
  { path: 'login', component: Login },
  { path: 'logout', component: Logout },
  { path: '**', redirectTo: '' },
];
