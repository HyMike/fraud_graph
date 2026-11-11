import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth';
import { API_BASE_URL } from './ring-detector-api';

/**
 * Attaches the Bearer token to ring-detector calls (and only those), and treats
 * a 401 on an authenticated call as an expired/invalid session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  if (!token || !req.url.startsWith(API_BASE_URL)) {
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
