import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from '../services/auth';

/**
 * Attaches the Bearer token to ring-detector calls (and only those), and treats
 * a 401 on an authenticated call as an expired/invalid session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  if (!token || !req.url.startsWith(environment.apiBaseUrl)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        auth.logout('expired');
      }
      return throwError(() => err);
    }),
  );
};
