import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { OrganizationService } from '../core/services/organization.service';
import { Organization } from '../core/models/user.model';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="auth-container">
      <div class="auth-card">
        <h2>Register</h2>
        @if (error()) {
          <div class="error-banner">{{ error() }}</div>
        }
        <form (ngSubmit)="onSubmit()">
          <div class="form-group">
            <label for="username">Username</label>
            <input id="username" type="text" [(ngModel)]="username" name="username" required>
          </div>
          <div class="form-group">
            <label for="email">Email</label>
            <input id="email" type="email" [(ngModel)]="email" name="email" required>
          </div>
          <div class="form-group">
            <label for="password">Password</label>
            <input id="password" type="password" [(ngModel)]="password" name="password" required>
            <small class="hint">Min 10 chars, 1 number, 1 symbol</small>
          </div>
          <div class="form-group">
            <label for="site">Site (optional)</label>
            <select id="site" [(ngModel)]="siteId" name="site">
              <option [ngValue]="null">— None —</option>
              @for (site of sites(); track site.id) {
                <option [ngValue]="site.id">{{ site.name }}</option>
              }
            </select>
          </div>
          <button type="submit" [disabled]="loading()">
            {{ loading() ? 'Creating...' : 'Create Account' }}
          </button>
        </form>
        <p class="auth-link">
          Already have an account? <a routerLink="/auth/login">Sign in</a>
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
    input, select {
      width: 100%; padding: 0.75rem; border: 1px solid #ddd;
      border-radius: 4px; font-size: 1rem; box-sizing: border-box;
    }
    .hint { color: #888; font-size: 0.8rem; }
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
export class RegisterComponent implements OnInit {
  username = '';
  email = '';
  password = '';
  siteId: number | null = null;
  sites = signal<Organization[]>([]);
  loading = signal(false);
  error = signal('');

  constructor(
    private authService: AuthService,
    private orgService: OrganizationService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.orgService.getByLevel('SITE').subscribe({
      next: (sites) => this.sites.set(sites),
      error: () => {} // sites are optional during registration
    });
  }

  onSubmit(): void {
    this.loading.set(true);
    this.error.set('');

    this.authService.register({
      username: this.username,
      email: this.email,
      password: this.password,
      siteId: this.siteId ?? undefined
    }).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (err) => {
        const msg = err.error?.error || err.error?.details?.password || 'Registration failed';
        this.error.set(msg);
        this.loading.set(false);
      }
    });
  }
}
