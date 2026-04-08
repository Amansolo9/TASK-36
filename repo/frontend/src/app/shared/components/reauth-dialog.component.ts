import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReauthService } from '../../core/services/reauth.service';

/**
 * Shared re-authentication dialog shown when a privileged action
 * requires the user to re-enter their password.
 *
 * Usage: include <app-reauth-dialog /> in your root app component template.
 * The dialog visibility is controlled by ReauthService.dialogVisible.
 */
@Component({
  selector: 'app-reauth-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    @if (reauth.dialogVisible()) {
      <div class="reauth-overlay" (click)="reauth.cancel()">
        <div class="reauth-dialog" (click)="$event.stopPropagation()">
          <h3>Re-authentication Required</h3>
          <p>This action requires you to verify your identity. Please re-enter your password.</p>

          @if (reauth.dialogError()) {
            <div class="error-banner">{{ reauth.dialogError() }}</div>
          }

          <form (ngSubmit)="submit()">
            <label for="reauth-password">Password</label>
            <input
              id="reauth-password"
              type="password"
              [(ngModel)]="password"
              name="password"
              placeholder="Enter your password"
              [disabled]="reauth.loading()"
              autocomplete="current-password"
            >
            <div class="dialog-actions">
              <button type="button" class="btn-cancel" (click)="reauth.cancel()" [disabled]="reauth.loading()">
                Cancel
              </button>
              <button type="submit" class="btn-submit" [disabled]="!password || reauth.loading()">
                {{ reauth.loading() ? 'Verifying...' : 'Verify' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
  styles: [`
    .reauth-overlay {
      position: fixed; top: 0; left: 0; right: 0; bottom: 0;
      background: rgba(0,0,0,0.5); z-index: 1000;
      display: flex; align-items: center; justify-content: center;
    }
    .reauth-dialog {
      background: white; border-radius: 8px; padding: 1.5rem; width: 400px; max-width: 90vw;
      box-shadow: 0 4px 20px rgba(0,0,0,0.3);
    }
    .reauth-dialog h3 { margin-top: 0; margin-bottom: 0.5rem; }
    .reauth-dialog p { color: #666; margin-bottom: 1rem; font-size: 0.9rem; }
    .error-banner {
      background: #ffebee; color: #c62828; padding: 0.5rem; border-radius: 4px;
      margin-bottom: 0.75rem; font-size: 0.85rem;
    }
    .reauth-dialog label { display: block; font-weight: 500; margin-bottom: 0.25rem; }
    .reauth-dialog input {
      width: 100%; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px;
      box-sizing: border-box; font-size: 1rem; margin-bottom: 1rem;
    }
    .reauth-dialog input:focus { outline: none; border-color: #1976d2; }
    .dialog-actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
    .btn-cancel {
      padding: 0.5rem 1rem; background: white; border: 1px solid #ddd;
      border-radius: 4px; cursor: pointer;
    }
    .btn-submit {
      padding: 0.5rem 1rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .btn-submit:disabled { opacity: 0.5; }
  `]
})
export class ReauthDialogComponent {
  password = '';

  constructor(public reauth: ReauthService) {}

  submit(): void {
    if (this.password) {
      this.reauth.submitPassword(this.password);
      this.password = '';
    }
  }
}
