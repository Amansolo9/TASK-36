import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="admin-page">
      <h2>Governance Dashboard</h2>

      <div class="tab-bar">
        @if (isEnterpriseAdmin()) {
          <button [class.active]="activeTab() === 'incentives'" (click)="activeTab.set('incentives')">Incentive Rules</button>
          <button [class.active]="activeTab() === 'audit'" (click)="activeTab.set('audit')">Audit Trail</button>
        }
        <button [class.active]="activeTab() === 'appeals'" (click)="activeTab.set('appeals')">Pending Appeals</button>
        <button [class.active]="activeTab() === 'tickets'" (click)="activeTab.set('tickets')">Open Tickets</button>
      </div>

      <!-- Incentive Rules Tab -->
      @if (activeTab() === 'incentives') {
        <div class="section">
          <h3>Incentive Rules</h3>
          @for (rule of incentiveRules(); track rule.actionKey) {
            <div class="rule-card">
              <span class="rule-key">{{ rule.actionKey }}</span>
              <span>{{ rule.description }}</span>
              <div class="rule-edit">
                <input type="number" [(ngModel)]="rule.points" style="width: 80px;">
                <button (click)="updateRule(rule)">Save</button>
                <label><input type="checkbox" [checked]="rule.active" (change)="toggleRule(rule)"> Active</label>
              </div>
            </div>
          }
        </div>
      }

      <!-- Audit Trail Tab -->
      @if (activeTab() === 'audit') {
        <div class="section">
          <h3>Recent Audit Trail</h3>
          <div class="audit-controls">
            <input type="datetime-local" [(ngModel)]="auditStart">
            <input type="datetime-local" [(ngModel)]="auditEnd">
            <button (click)="loadAudit()">Search</button>
          </div>
          @for (entry of auditEntries(); track entry.id) {
            <div class="audit-row">
              <span class="audit-action">{{ entry.action }}</span>
              <span>{{ entry.entityType }} #{{ entry.entityId }}</span>
              <span>by {{ entry.username || 'SYSTEM' }}</span>
              <small>{{ entry.createdAt | date:'medium' }}</small>
            </div>
          }
        </div>
      }

      <!-- Pending Appeals Tab -->
      @if (activeTab() === 'appeals') {
        <div class="section">
          <h3>Pending Appeals</h3>
          @for (appeal of pendingAppeals(); track appeal.id) {
            <div class="appeal-card">
              <div><strong>Rating #{{ appeal.id }}</strong> — Order #{{ appeal.orderId }}</div>
              <div>Stars: {{ appeal.stars }} | Status: {{ appeal.appealStatus }}</div>
              @if (appeal.arbitrationNotes) { <div>Notes: {{ appeal.arbitrationNotes }}</div> }
              <div class="appeal-actions">
                <input [(ngModel)]="appealNotes[appeal.id]" placeholder="Resolution notes...">
                <select [(ngModel)]="appealResolution[appeal.id]">
                  <option value="UPHELD">Uphold</option>
                  <option value="OVERTURNED">Overturn</option>
                  <option value="IN_ARBITRATION">Move to Arbitration</option>
                </select>
                <button (click)="resolveAppeal(appeal.id)">Resolve</button>
              </div>
            </div>
          }
          @if (pendingAppeals().length === 0) { <p>No pending appeals.</p> }
        </div>
      }

      <!-- Tickets Tab -->
      @if (activeTab() === 'tickets') {
        <div class="section">
          <h3>Open Tickets</h3>
          @for (ticket of openTickets(); track ticket.id) {
            <div class="ticket-card">
              <div><strong>#{{ ticket.id }}</strong> — {{ ticket.type }} — {{ ticket.status }}</div>
              <div>{{ ticket.description | slice:0:100 }}...</div>
              @if (ticket.slaBreached) { <span class="sla-badge">SLA Breached</span> }
              <div class="ticket-actions">
                <select [(ngModel)]="ticketStatus[ticket.id]">
                  <option value="UNDER_REVIEW">Under Review</option>
                  <option value="APPROVED">Approve</option>
                  <option value="REJECTED">Reject</option>
                  <option value="CLOSED">Close</option>
                </select>
                <button (click)="updateTicketStatus(ticket.id)">Update</button>
              </div>
            </div>
          }
          @if (openTickets().length === 0) { <p>No open tickets.</p> }
        </div>
      }
    </div>
  `,
  styles: [`
    .admin-page { max-width: 1000px; margin: 2rem auto; padding: 0 1rem; }
    .tab-bar { display: flex; gap: 0.5rem; margin-bottom: 1.5rem; }
    .tab-bar button { padding: 0.5rem 1rem; border: 1px solid #ddd; background: white; border-radius: 4px; cursor: pointer; }
    .tab-bar button.active { background: #1976d2; color: white; border-color: #1976d2; }
    .section { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
    .section h3 { margin-top: 0; }
    .rule-card { display: flex; align-items: center; gap: 1rem; padding: 0.75rem 0; border-bottom: 1px solid #eee; flex-wrap: wrap; }
    .rule-key { font-weight: 700; min-width: 150px; font-family: monospace; }
    .rule-edit { display: flex; align-items: center; gap: 0.5rem; margin-left: auto; }
    .rule-edit input[type="number"] { padding: 0.3rem; border: 1px solid #ddd; border-radius: 4px; }
    .rule-edit button { padding: 0.3rem 0.75rem; background: #1976d2; color: white; border: none; border-radius: 4px; cursor: pointer; }
    .audit-controls { display: flex; gap: 0.5rem; margin-bottom: 1rem; }
    .audit-controls input { padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .audit-controls button { padding: 0.4rem 0.8rem; background: #1976d2; color: white; border: none; border-radius: 4px; cursor: pointer; }
    .audit-row { display: flex; gap: 1rem; padding: 0.5rem 0; border-bottom: 1px solid #f5f5f5; align-items: center; flex-wrap: wrap; }
    .audit-action { background: #e3f2fd; color: #1565c0; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.8rem; font-weight: 600; }
    .appeal-card, .ticket-card { background: #f9f9f9; padding: 1rem; border-radius: 4px; margin-bottom: 0.75rem; }
    .appeal-actions, .ticket-actions { display: flex; gap: 0.5rem; margin-top: 0.5rem; align-items: center; }
    .appeal-actions input { flex: 1; padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .appeal-actions select, .ticket-actions select { padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .appeal-actions button, .ticket-actions button { padding: 0.4rem 0.8rem; background: #1976d2; color: white; border: none; border-radius: 4px; cursor: pointer; }
    .sla-badge { background: #ffebee; color: #c62828; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 700; }
  `]
})
export class AdminComponent implements OnInit {
  activeTab = signal<string>('incentives');
  incentiveRules = signal<any[]>([]);
  auditEntries = signal<any[]>([]);
  pendingAppeals = signal<any[]>([]);
  openTickets = signal<any[]>([]);

  auditStart = '';
  auditEnd = '';
  appealNotes: Record<number, string> = {};
  appealResolution: Record<number, string> = {};
  ticketStatus: Record<number, string> = {};

  constructor(private http: HttpClient, public authService: AuthService) {}

  isEnterpriseAdmin(): boolean {
    return this.authService.userRole() === 'ENTERPRISE_ADMIN';
  }

  ngOnInit(): void {
    // Set default tab based on role
    if (!this.isEnterpriseAdmin()) {
      this.activeTab.set('appeals');
    }
    // Enterprise-only endpoints
    if (this.isEnterpriseAdmin()) {
      this.http.get<any[]>('/api/admin/incentive-rules').subscribe({
        next: r => this.incentiveRules.set(r), error: () => {}
      });
    }
    // Manager/admin endpoints
    this.http.get<any[]>('/api/ratings/appeals/pending').subscribe({
      next: a => this.pendingAppeals.set(a), error: () => {}
    });
    this.http.get<any[]>('/api/tickets/status/OPEN').subscribe({
      next: t => this.openTickets.set(t), error: () => {}
    });
  }

  loadAudit(): void {
    if (!this.auditStart || !this.auditEnd) return;
    const start = new Date(this.auditStart).toISOString();
    const end = new Date(this.auditEnd).toISOString();
    this.http.get<any[]>(`/api/audit/range?start=${start}&end=${end}`).subscribe(e => this.auditEntries.set(e));
  }

  updateRule(rule: any): void {
    this.http.put(`/api/admin/incentive-rules/${rule.actionKey}?points=${rule.points}`, null).subscribe();
  }

  toggleRule(rule: any): void {
    rule.active = !rule.active;
    this.http.patch(`/api/admin/incentive-rules/${rule.actionKey}/toggle?active=${rule.active}`, null).subscribe();
  }

  resolveAppeal(ratingId: number): void {
    const resolution = this.appealResolution[ratingId] || 'UPHELD';
    const notes = this.appealNotes[ratingId] || '';
    this.http.patch(`/api/ratings/${ratingId}/appeal/resolve?resolution=${resolution}&notes=${encodeURIComponent(notes)}`, null).subscribe({
      next: () => this.pendingAppeals.update(a => a.filter(x => x.id !== ratingId))
    });
  }

  updateTicketStatus(ticketId: number): void {
    const status = this.ticketStatus[ticketId];
    if (!status) return;
    this.http.patch(`/api/tickets/${ticketId}/status?status=${status}`, null).subscribe({
      next: () => this.openTickets.update(t => t.filter(x => x.id !== ticketId))
    });
  }
}
