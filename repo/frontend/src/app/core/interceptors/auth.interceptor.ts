import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ReauthService } from '../services/reauth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const reauthService = inject(ReauthService);
  const token = authService.getToken();

  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` }
    });
  }

  return next(req).pipe(
    tap(event => {
      // Sliding session: pick up refreshed token from backend
      if ('headers' in event && event.headers) {
        const refreshed = event.headers.get('X-Refreshed-Token');
        if (refreshed) {
          localStorage.setItem('storehub_token', refreshed);
        }
      }
    }),
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        authService.logout();
        return throwError(() => error);
      }

      // Detect recent-auth required (403 with code RECENT_AUTH_REQUIRED)
      if (reauthService.isRecentAuthError(error)) {
        return reauthService.promptReauth().pipe(
          switchMap(result => {
            if (result.success) {
              // Retry original request with refreshed token
              const freshToken = authService.getToken();
              const retryReq = req.clone({
                setHeaders: { Authorization: `Bearer ${freshToken}` }
              });
              return next(retryReq);
            }
            // User cancelled — propagate original error
            return throwError(() => error);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
