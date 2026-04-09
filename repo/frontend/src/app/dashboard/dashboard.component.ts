import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/services/auth.service';
import { UserService } from '../core/services/user.service';
import { OrderService } from '../core/services/order.service';
import { CheckInService } from '../core/services/checkin.service';
import { DataMaskPipe } from '../core/pipes/data-mask.pipe';
import { User } from '../core/models/user.model';
import { OrderResponse } from '../core/models/fulfillment.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, DataMaskPipe],
  template: `
    <div class="dashboard">
      <main class="content">
        <div class="welcome-card">
          <h2>Welcome, {{ authService.currentUser()?.username }}</h2>
          <p>Role: <strong>{{ authService.userRole() }}</strong></p>
          @if (authService.currentUser()?.siteId) {
            <p>Site ID: <strong>{{ authService.currentUser()?.siteId }}</strong></p>
          }
        </div>

        <div class="section" style="margin-bottom: 2rem;">
          <h3>Check-In Status</h3>
          <div class="status-grid">
            <div class="status-card">
              <span class="chip chip-on-time">On Time</span>
              <span class="count">{{ checkinCounts().onTime }}</span>
            </div>
            <div class="status-card">
              <span class="chip chip-late">Late</span>
              <span class="count">{{ checkinCounts().late }}</span>
            </div>
            <div class="status-card">
              <span class="chip chip-flagged">Flagged</span>
              <span class="count">{{ checkinCounts().flagged }}</span>
            </div>
          </div>
        </div>

        <div class="section" style="margin-bottom: 2rem;">
          <h3>Order Activity</h3>
          <div class="status-grid">
            @for (item of orderStatusSummary(); track item.status) {
              <div class="status-card">
                <span class="chip" [class]="'chip-' + item.status.toLowerCase()">{{ item.status }}</span>
                <span class="count">{{ item.count }}</span>
              </div>
            }
          </div>
          @if (orderStatusSummary().length === 0) {
            <p style="color: #888;">No orders yet.</p>
          }
        </div>

        @if (isManager()) {
          <div class="section">
            <h3>User Management</h3>
            @if (users().length === 0) {
              <p>Loading users...</p>
            } @else {
              <table class="data-table">
                <thead>
                  <tr>
                    <th>Username</th>
                    <th>Email</th>
                    <th>Role</th>
                    <th>Site</th>
                    <th>Address</th>
                    <th>Device</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  @for (user of users(); track user.id) {
                    <tr>
                      <td>{{ user.username }}</td>
                      <td>{{ user.email | dataMask:'email' }}</td>
                      <td><span class="role-badge small">{{ user.role }}</span></td>
                      <td>{{ user.siteName || '—' }}</td>
                      <td>{{ user.address | dataMask:'address' }}</td>
                      <td>{{ user.deviceId | dataMask:'deviceId' }}</td>
                      <td>
                        <span [class]="user.enabled ? 'status-active' : 'status-disabled'">
                          {{ user.enabled ? 'Active' : 'Disabled' }}
                        </span>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          </div>
        }
      </main>
    </div>
  `,
  styles: [`
    .dashboard { min-height: 100vh; background: #f5f5f5; }
    .content { max-width: 1200px; margin: 2rem auto; padding: 0 1rem; }
    .welcome-card {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 2rem;
    }
    .section { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
    .section h3 { margin-top: 0; }
    .data-table { width: 100%; border-collapse: collapse; }
    .data-table th, .data-table td {
      padding: 0.75rem; text-align: left; border-bottom: 1px solid #eee;
    }
    .data-table th { font-weight: 600; color: #555; background: #fafafa; }
    .role-badge {
      background: #e3f2fd; color: #1565c0; padding: 0.25rem 0.5rem;
      border-radius: 4px; font-size: 0.8rem; font-weight: 600;
    }
    .role-badge.small { font-size: 0.7rem; }
    .status-active { color: #2e7d32; font-weight: 500; }
    .status-disabled { color: #c62828; font-weight: 500; }
    .status-grid { display: flex; gap: 1rem; flex-wrap: wrap; }
    .status-card { background: #f9f9f9; padding: 0.75rem 1.25rem; border-radius: 8px; display: flex; align-items: center; gap: 0.75rem; }
    .chip { padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.75rem; font-weight: 600; text-transform: uppercase; }
    .chip-pending { background: #fff3e0; color: #e65100; }
    .chip-confirmed { background: #e3f2fd; color: #1565c0; }
    .chip-preparing { background: #f3e5f5; color: #7b1fa2; }
    .chip-ready_for_pickup { background: #e8f5e9; color: #2e7d32; }
    .chip-out_for_delivery { background: #e3f2fd; color: #1565c0; }
    .chip-delivered, .chip-picked_up { background: #e8f5e9; color: #2e7d32; }
    .chip-cancelled { background: #ffebee; color: #c62828; }
    .chip-on-time { background: #e8f5e9; color: #2e7d32; }
    .chip-late { background: #fff3e0; color: #e65100; }
    .chip-flagged { background: #ffebee; color: #c62828; }
    .count { font-size: 1.5rem; font-weight: 700; }
  `]
})
export class DashboardComponent implements OnInit {
  users = signal<User[]>([]);
  orderStatusSummary = signal<{status: string; count: number}[]>([]);
  checkinCounts = signal<{onTime: number; late: number; flagged: number}>({onTime: 0, late: 0, flagged: 0});

  constructor(
    public authService: AuthService,
    private userService: UserService,
    private orderService: OrderService,
    private checkInService: CheckInService
  ) {}

  ngOnInit(): void {
    this.orderService.getMyOrders().subscribe({
      next: (orders) => {
        const counts = new Map<string, number>();
        orders.forEach(o => counts.set(o.status, (counts.get(o.status) || 0) + 1));
        this.orderStatusSummary.set(
          Array.from(counts.entries()).map(([status, count]) => ({ status, count }))
        );
      }
    });

    // Load check-in status chips from live data
    const siteId = this.authService.currentUser()?.siteId;
    if (siteId) {
      const now = new Date();
      const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).toISOString();
      this.checkInService.getBySite(siteId, dayStart, now.toISOString()).subscribe({
        next: (checkins) => {
          let onTime = 0, late = 0, flagged = 0;
          checkins.forEach(c => {
            if (c.status === 'FLAGGED') flagged++;
            else if (c.windowClassification === 'LATE') late++;
            else onTime++;
          });
          this.checkinCounts.set({ onTime, late, flagged });
        },
        error: () => {}
      });
    }

    if (this.isManager()) {
      this.userService.getAll().subscribe({
        next: (users) => this.users.set(users),
        error: () => {}
      });
    }
  }

  isManager(): boolean {
    const role = this.authService.userRole();
    return role === 'ENTERPRISE_ADMIN' || role === 'SITE_MANAGER';
  }
}
