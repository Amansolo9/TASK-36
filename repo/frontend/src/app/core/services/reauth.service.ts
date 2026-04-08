import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, throwError } from 'rxjs';
import { switchMap, catchError, take } from 'rxjs/operators';
import { AuthService } from './auth.service';

/**
 * Manages the recent-auth recovery flow.
 *
 * When a privileged action returns 403 with code "RECENT_AUTH_REQUIRED",
 * this service prompts the user for password re-entry, calls the backend
 * reauth endpoint, and retries the original request.
 */
@Injectable({ providedIn: 'root' })
export class ReauthService {
  /** Whether the re-auth dialog is currently showing */
  dialogVisible = signal(false);

  /** Error message to display in the dialog */
  dialogError = signal<string | null>(null);

  /** Whether a re-auth request is in progress */
  loading = signal(false);

  private pendingRetry$ = new Subject<{ success: boolean }>();
  private pendingRequest: { url: string; method: string; body: any; headers: any } | null = null;

  constructor(private authService: AuthService) {}

  /**
   * Returns true if the error response indicates recent-auth is required.
   */
  isRecentAuthError(error: HttpErrorResponse): boolean {
    return error.status === 403 && error.error?.code === 'RECENT_AUTH_REQUIRED';
  }

  /**
   * Opens the re-auth dialog and returns an observable that emits
   * once the user completes (or cancels) re-authentication.
   */
  promptReauth(): Observable<{ success: boolean }> {
    this.dialogError.set(null);
    this.dialogVisible.set(true);
    return this.pendingRetry$.pipe(take(1));
  }

  /**
   * Called by the dialog when the user submits their password.
   */
  submitPassword(password: string): void {
    this.loading.set(true);
    this.dialogError.set(null);

    this.authService.reauthenticate(password).subscribe({
      next: () => {
        this.loading.set(false);
        this.dialogVisible.set(false);
        this.pendingRetry$.next({ success: true });
      },
      error: (err) => {
        this.loading.set(false);
        this.dialogError.set(
          err.status === 401 ? 'Invalid password. Please try again.' : 'Re-authentication failed.'
        );
        // Don't close dialog — let user retry or cancel
      }
    });
  }

  /**
   * Called when the user cancels the re-auth dialog.
   */
  cancel(): void {
    this.dialogVisible.set(false);
    this.loading.set(false);
    this.dialogError.set(null);
    this.pendingRetry$.next({ success: false });
  }
}
