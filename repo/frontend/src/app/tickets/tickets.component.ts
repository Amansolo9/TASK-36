import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TicketService } from '../core/services/ticket.service';
import { AuthService } from '../core/services/auth.service';
import { TicketResponse, TicketType } from '../core/models/ticket.model';

@Component({
  selector: 'app-tickets',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="tickets-page">
      <h2>Support Tickets</h2>

      <!-- Create Ticket -->
      <div class="create-ticket-card">
        <h3>New Ticket</h3>
        <div class="form-row">
          <div class="form-group">
            <label>Order ID</label>
            <input type="number" [(ngModel)]="newOrderId" placeholder="Order ID">
          </div>
          <div class="form-group">
            <label>Type</label>
            <select [(ngModel)]="newType">
              <option value="REFUND_ONLY">Refund Only</option>
              <option value="RETURN_AND_REFUND">Return &amp; Refund</option>
            </select>
          </div>
        </div>
        <div class="form-group">
          <label>Description</label>
          <textarea [(ngModel)]="newDescription" rows="3" placeholder="Describe the issue..."></textarea>
        </div>
        <button (click)="createTicket()" [disabled]="!newOrderId || !newDescription">Submit Ticket</button>
      </div>

      @if (error()) {
        <div class="error-msg">{{ error() }}</div>
      }

      <!-- Ticket List -->
      @for (ticket of tickets(); track ticket.id) {
        <div class="ticket-card" [class.sla-breached]="ticket.slaBreached">
          <div class="ticket-header">
            <span class="ticket-id">#{{ ticket.id }}</span>
            <span class="type-badge">{{ ticket.type }}</span>
            <span class="status-badge" [class]="'status-' + ticket.status.toLowerCase()">
              {{ ticket.status }}
            </span>
            @if (ticket.autoApproved) {
              <span class="auto-badge">Auto-Approved</span>
            }
            @if (ticket.slaBreached) {
              <span class="sla-badge">SLA Breached</span>
            }
          </div>
          <p>{{ ticket.description }}</p>
          <div class="ticket-meta">
            <span>Order #{{ ticket.orderId }}</span>
            @if (ticket.refundAmount) {
              <span>Refund: \${{ ticket.refundAmount }}</span>
            }
            <span>{{ ticket.createdAt | date:'medium' }}</span>
          </div>

          <!-- Evidence -->
          <div class="evidence-section">
            <strong>Evidence ({{ ticket.evidence.length }})</strong>
            @for (ev of ticket.evidence; track ev.id) {
              <div class="evidence-item">
                <span>{{ ev.fileName }} ({{ (ev.fileSize / 1024) | number:'1.0-0' }} KB)</span>
                <code class="hash">SHA-256: {{ ev.sha256Hash.substring(0, 16) }}...</code>
              </div>
            }
            <div class="upload-row">
              <input type="file" (change)="onFileSelected($event, ticket.id)"
                     accept=".pdf,.jpg,.jpeg,.png">
            </div>
          </div>
        </div>
      }

      @if (tickets().length === 0 && !loading()) {
        <p class="empty">No support tickets.</p>
      }
    </div>
  `,
  styles: [`
    .tickets-page { max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
    .create-ticket-card {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .create-ticket-card h3 { margin-top: 0; }
    .form-row { display: flex; gap: 1rem; }
    .form-group { flex: 1; margin-bottom: 1rem; }
    label { display: block; margin-bottom: 0.3rem; font-weight: 500; }
    input, select, textarea {
      width: 100%; padding: 0.5rem; border: 1px solid #ddd;
      border-radius: 4px; box-sizing: border-box; font-family: inherit;
    }
    .create-ticket-card button {
      padding: 0.5rem 1.5rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .create-ticket-card button:disabled { opacity: 0.5; }
    .error-msg { background: #ffebee; color: #c62828; padding: 0.75rem; border-radius: 4px; margin-bottom: 1rem; }
    .ticket-card {
      background: white; padding: 1.25rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 0.75rem;
      border-left: 4px solid #1976d2;
    }
    .ticket-card.sla-breached { border-left-color: #c62828; }
    .ticket-header { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; margin-bottom: 0.5rem; }
    .ticket-id { font-weight: 700; }
    .type-badge { background: #f3e5f5; color: #7b1fa2; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.75rem; }
    .status-badge { padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 600; }
    .status-open { background: #fff3e0; color: #e65100; }
    .status-under_review { background: #e3f2fd; color: #1565c0; }
    .status-approved, .status-refunded { background: #e8f5e9; color: #2e7d32; }
    .status-rejected, .status-escalated { background: #ffebee; color: #c62828; }
    .status-closed { background: #e0e0e0; color: #616161; }
    .auto-badge { background: #e8f5e9; color: #2e7d32; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.75rem; }
    .sla-badge { background: #ffebee; color: #c62828; padding: 0.15rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 700; }
    .ticket-meta { color: #888; font-size: 0.85rem; display: flex; gap: 1rem; margin-top: 0.5rem; }
    .evidence-section { margin-top: 0.75rem; padding-top: 0.5rem; border-top: 1px solid #eee; }
    .evidence-item { display: flex; justify-content: space-between; align-items: center; padding: 0.3rem 0; }
    .hash { font-size: 0.7rem; color: #888; }
    .upload-row { margin-top: 0.5rem; }
    .empty { text-align: center; color: #888; }
  `]
})
export class TicketsComponent implements OnInit {
  tickets = signal<TicketResponse[]>([]);
  loading = signal(false);
  error = signal('');

  newOrderId: number | null = null;
  newType: TicketType = 'REFUND_ONLY';
  newDescription = '';

  constructor(
    private ticketService: TicketService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading.set(true);
    this.ticketService.getMyTickets().subscribe({
      next: (t) => { this.tickets.set(t); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  async createTicket(): Promise<void> {
    if (!this.newOrderId || !this.newDescription) return;
    this.error.set('');

    try {
      const result = await this.ticketService.createOffline({
        orderId: this.newOrderId,
        type: this.newType,
        description: this.newDescription
      });
      if (result.outcome === 'synced') {
        this.loadTickets();
      }
      this.newOrderId = null;
      this.newDescription = '';
    } catch (err: any) {
      this.error.set(err.error?.error || 'Failed to create ticket');
    }
  }

  onFileSelected(event: Event, ticketId: number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.ticketService.uploadEvidence(ticketId, file).subscribe({
      next: (evidence) => {
        this.tickets.update(tickets => tickets.map(t => {
          if (t.id === ticketId) {
            return { ...t, evidence: [...t.evidence, evidence] };
          }
          return t;
        }));
      },
      error: (err) => this.error.set(err.error?.error || 'Upload failed')
    });
  }
}
