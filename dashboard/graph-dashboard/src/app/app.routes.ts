import { Routes } from '@angular/router';
import { authGuard } from './guards/auth-guard';
import { Dashboard } from './components/dashboard/dashboard';
import { Login } from './components/login/login';
import { Logout } from './components/logout/logout';

export const routes: Routes = [
  { path: '', component: Dashboard, canActivate: [authGuard] },
  { path: 'login', component: Login },
  { path: 'logout', component: Logout },
  { path: '**', redirectTo: '' },
];
