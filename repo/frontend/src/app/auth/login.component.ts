import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <h2>Sign In</h2>
        @if (error()) {
          <div class="error-banner">{{ error() }}</div>
        }
        <form (ngSubmit)="onSubmit()">
          <div class="form-group">
            <label for="username">Username</label>
            <input id="username" type="text" [(ngModel)]="username" name="username"
                   required autocomplete="username">
          </div>
          <div class="form-group">
            <label for="password">Password</label>
            <input id="password" type="password" [(ngModel)]="password" name="password"
                   required autocomplete="current-password">
          </div>
          <button type="submit" [disabled]="loading()">
            {{ loading() ? 'Signing in...' : 'Sign In' }}
          </button>
        </form>
        <p class="auth-link">
          Don't have an account? <a routerLink="/auth/register">Register</a>
        </p>
      </div>
    </div>
  `,
  styles: [`
    .auth-container {
      display: flex; justify-content: center; align-items: center;
      min-height: 100vh; background: #f5f5f5;
    }
    .auth-card {
      background: white; padding: 2rem; border-radius: 8px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.1); width: 100%; max-width: 400px;
    }
    h2 { text-align: center; margin-bottom: 1.5rem; color: #333; }
    .form-group { margin-bottom: 1rem; }
    label { display: block; margin-bottom: 0.5rem; font-weight: 500; color: #555; }
    input {
      width: 100%; padding: 0.75rem; border: 1px solid #ddd;
      border-radius: 4px; font-size: 1rem; box-sizing: border-box;
    }
    button {
      width: 100%; padding: 0.75rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; font-size: 1rem; cursor: pointer;
      margin-top: 0.5rem;
    }
    button:hover:not(:disabled) { background: #1565c0; }
    button:disabled { opacity: 0.7; cursor: not-allowed; }
    .error-banner {
      background: #ffebee; color: #c62828; padding: 0.75rem;
      border-radius: 4px; margin-bottom: 1rem; text-align: center;
    }
    .auth-link { text-align: center; margin-top: 1rem; }
    .auth-link a { color: #1976d2; text-decoration: none; }
  `]
})
export class LoginComponent {
  username = '';
  password = '';
  loading = signal(false);
  error = signal('');

  constructor(private authService: AuthService, private router: Router) {}

  onSubmit(): void {
    this.loading.set(true);
    this.error.set('');

    this.authService.login({ username: this.username, password: this.password }).subscribe({
      next: () => {
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.error.set(err.error?.error || 'Login failed');
        this.loading.set(false);
      }
    });
  }
}
