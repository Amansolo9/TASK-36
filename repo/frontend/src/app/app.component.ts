import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (authService.isAuthenticated()) {
      <nav class="main-nav">
        <div class="nav-brand">StoreHub</div>
        <div class="nav-links">
          <a routerLink="/dashboard" routerLinkActive="active">Dashboard</a>
          <a routerLink="/checkin" routerLinkActive="active">Check-In</a>
          <a routerLink="/orders" routerLinkActive="active">Orders</a>
          <a routerLink="/ratings" routerLinkActive="active">Ratings</a>
          <a routerLink="/community" routerLinkActive="active">Community</a>
          <a routerLink="/community/dashboard" routerLinkActive="active">Points</a>
          <a routerLink="/tickets" routerLinkActive="active">Tickets</a>
          @if (isManager()) {
            <a routerLink="/analytics" routerLinkActive="active">Analytics</a>
            <a routerLink="/admin" routerLinkActive="active">Admin</a>
          }
        </div>
        <div class="nav-user">
          <span class="nav-role">{{ authService.userRole() }}</span>
          <span>{{ authService.currentUser()?.username }}</span>
          <button class="btn-nav-logout" (click)="authService.logout()">Logout</button>
        </div>
      </nav>
    }
    <router-outlet />
  `,
  styles: [`
    :host { display: block; }
    .main-nav {
      background: #1976d2; color: white; padding: 0 1.5rem;
      display: flex; align-items: center; height: 56px; gap: 1.5rem;
    }
    .nav-brand { font-weight: 700; font-size: 1.25rem; white-space: nowrap; }
    .nav-links { display: flex; gap: 0.25rem; flex: 1; overflow-x: auto; }
    .nav-links a {
      color: rgba(255,255,255,0.8); text-decoration: none; padding: 0.4rem 0.75rem;
      border-radius: 4px; font-size: 0.9rem; white-space: nowrap;
    }
    .nav-links a:hover { background: rgba(255,255,255,0.15); color: white; }
    .nav-links a.active { background: rgba(255,255,255,0.2); color: white; font-weight: 600; }
    .nav-user { display: flex; align-items: center; gap: 0.75rem; white-space: nowrap; }
    .nav-role {
      background: rgba(255,255,255,0.2); padding: 0.2rem 0.5rem;
      border-radius: 4px; font-size: 0.75rem;
    }
    .btn-nav-logout {
      background: rgba(255,255,255,0.15); border: 1px solid rgba(255,255,255,0.3);
      color: white; padding: 0.3rem 0.75rem; border-radius: 4px; cursor: pointer;
      font-size: 0.85rem;
    }
    .btn-nav-logout:hover { background: rgba(255,255,255,0.25); }
  `]
})
export class AppComponent {
  constructor(public authService: AuthService) {}

  isManager(): boolean {
    const role = this.authService.userRole();
    return role === 'ENTERPRISE_ADMIN' || role === 'SITE_MANAGER';
  }
}
