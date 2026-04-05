import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'auth/login',
    loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'auth/register',
    loadComponent: () => import('./auth/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'checkin',
    loadComponent: () => import('./checkin/checkin.component').then(m => m.CheckInComponent),
    canActivate: [authGuard, roleGuard('ENTERPRISE_ADMIN', 'SITE_MANAGER', 'TEAM_LEAD', 'STAFF')]
  },
  {
    path: 'orders',
    loadComponent: () => import('./orders/orders.component').then(m => m.OrdersComponent),
    canActivate: [authGuard]
  },
  {
    path: 'ratings',
    loadComponent: () => import('./ratings/ratings.component').then(m => m.RatingsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'community',
    loadComponent: () => import('./community/community-feed.component').then(m => m.CommunityFeedComponent),
    canActivate: [authGuard]
  },
  {
    path: 'community/dashboard',
    loadComponent: () => import('./community/community-dashboard.component').then(m => m.CommunityDashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'tickets',
    loadComponent: () => import('./tickets/tickets.component').then(m => m.TicketsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'analytics',
    loadComponent: () => import('./analytics/site-performance.component').then(m => m.SitePerformanceComponent),
    canActivate: [authGuard, roleGuard('ENTERPRISE_ADMIN', 'SITE_MANAGER')]
  },
  {
    path: 'admin',
    loadComponent: () => import('./admin/admin.component').then(m => m.AdminComponent),
    canActivate: [authGuard, roleGuard('ENTERPRISE_ADMIN', 'SITE_MANAGER')]
  },
  { path: '**', redirectTo: 'dashboard' }
];
