import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { OrderService } from '../core/services/order.service';
import { OrganizationService } from '../core/services/organization.service';
import { AuthService } from '../core/services/auth.service';
import { OrderResponse, FulfillmentMode } from '../core/models/fulfillment.model';
import { Organization } from '../core/models/user.model';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="orders-page">
      <h2>My Orders</h2>

      <div class="create-order-card">
        <h3>Place New Order</h3>
        @if (createError()) { <div class="error-msg">{{ createError() }}</div> }
        <div class="form-row">
          <div class="form-group">
            <label>Site</label>
            <select [(ngModel)]="newSiteId">
              @for (site of sites(); track site.id) {
                <option [ngValue]="site.id">{{ site.name }}</option>
              }
            </select>
          </div>
          <div class="form-group">
            <label>Subtotal ($)</label>
            <input type="number" [(ngModel)]="newSubtotal" min="0" step="0.01" placeholder="0.00">
          </div>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>Fulfillment</label>
            <select [(ngModel)]="fulfillmentMode">
              <option value="DELIVERY">Delivery</option>
              <option value="PICKUP">Pickup</option>
              <option value="COURIER_HANDOFF">Courier Handoff</option>
            </select>
          </div>
          @if (fulfillmentMode !== 'PICKUP') {
            <div class="form-group">
              <label>Delivery ZIP</label>
              <input type="text" [(ngModel)]="newDeliveryZip" placeholder="ZIP code" maxlength="10">
            </div>
          }
        </div>
        <button (click)="createOrder()" [disabled]="!newSiteId || !newSubtotal">Place Order</button>
      </div>

      @if (orders().length === 0 && !loading()) {
        <p class="empty">No orders yet.</p>
      }

      @for (order of orders(); track order.id) {
        <div class="order-card">
          <div class="order-header">
            <span class="order-id">#{{ order.id }}</span>
            <span class="status" [class]="'status-' + order.status.toLowerCase()">{{ order.status }}</span>
          </div>
          <div class="order-details">
            <p>Total: <strong>\${{ order.total }}</strong></p>
            <p>Type: <strong>{{ order.pickup ? 'Pickup' : 'Delivery' }}</strong></p>
            @if (order.pickup && order.pickupVerificationCode && !order.pickupVerified) {
              <div class="verification-box">
                <p>Your Pickup Code: <code>{{ order.pickupVerificationCode }}</code></p>
                <p><small>Present this code to staff at pickup</small></p>
              </div>
            }
            @if (order.pickupVerified) {
              <p class="verified">Pickup Verified</p>
            }
            @if (!order.pickup && order.deliveryZip) {
              <p>Delivery ZIP: {{ order.deliveryZip }} ({{ order.deliveryDistanceMiles }} mi)</p>
              <button class="btn-label" (click)="downloadLabel(order.id)">Download Label</button>
            }
          </div>
          <div class="order-footer">
            <small>{{ order.createdAt | date:'medium' }}</small>
          </div>
        </div>
      }

      @if (isStaff()) {
        <h2 style="margin-top: 2rem;">Site Task Queue</h2>
        @for (order of siteOrders(); track order.id) {
          <div class="order-card">
            <div class="order-header">
              <span class="order-id">#{{ order.id }}</span>
              <span class="status" [class]="'status-' + order.status.toLowerCase()">{{ order.status }}</span>
              <span class="type-badge">{{ order.fulfillmentMode }}</span>
            </div>
            <div class="order-details">
              <p>Customer: #{{ order.customerId }} | Total: \${{ order.total }}</p>
              @if (order.fulfillmentMode === 'PICKUP' && !order.pickupVerified) {
                <div class="verify-row">
                  <input [(ngModel)]="rowVerifyCode[order.id]" placeholder="Verification code" maxlength="6">
                  <button (click)="staffVerifyPickup(order.id)">Verify Handoff</button>
                </div>
              }
              @if (order.pickupVerified) {
                <p class="verified">Pickup Verified by #{{ order.verifiedById }}</p>
              }
              <div class="status-actions">
                <select [(ngModel)]="rowStatus[order.id]">
                  <option value="">-- Update Status --</option>
                  <option value="CONFIRMED">Confirm</option>
                  <option value="PREPARING">Preparing</option>
                  <option value="READY_FOR_PICKUP">Ready for Pickup</option>
                  <option value="OUT_FOR_DELIVERY">Out for Delivery</option>
                  <option value="DELIVERED">Delivered</option>
                  <option value="COURIER_ASSIGNED">Courier Assigned</option>
                  <option value="COURIER_PICKED_UP">Courier Picked Up</option>
                  <option value="COURIER_DELIVERED">Courier Delivered</option>
                </select>
                <button (click)="updateOrderStatus(order.id)" [disabled]="!rowStatus[order.id]">Update</button>
              </div>
            </div>
            <div class="order-footer">
              <small>{{ order.createdAt | date:'medium' }}</small>
            </div>
          </div>
        }
        @if (siteOrders().length === 0) {
          <p class="empty">No site orders.</p>
        }
      }
    </div>
  `,
  styles: [`
    .orders-page { max-width: 800px; margin: 2rem auto; padding: 0 1rem; }
    .empty { text-align: center; color: #888; }
    .order-card {
      background: white; border-radius: 8px; padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1rem;
    }
    .order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .order-id { font-weight: 700; font-size: 1.1rem; }
    .status {
      padding: 0.25rem 0.75rem; border-radius: 12px; font-size: 0.8rem;
      font-weight: 600; text-transform: uppercase;
    }
    .status-pending { background: #fff3e0; color: #e65100; }
    .status-confirmed { background: #e3f2fd; color: #1565c0; }
    .status-preparing { background: #f3e5f5; color: #7b1fa2; }
    .status-ready_for_pickup { background: #e8f5e9; color: #2e7d32; }
    .status-out_for_delivery { background: #e3f2fd; color: #1565c0; }
    .status-delivered, .status-picked_up { background: #e8f5e9; color: #2e7d32; }
    .status-cancelled { background: #ffebee; color: #c62828; }
    .verification-box {
      background: #f5f5f5; padding: 1rem; border-radius: 4px; margin-top: 0.5rem;
    }
    .verification-box code { font-size: 1.3rem; font-weight: 700; color: #1976d2; }
    .verify-row { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
    .verify-row input { flex: 1; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }
    .verify-row button {
      padding: 0.5rem 1rem; background: #2e7d32; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .verified { color: #2e7d32; font-weight: 600; }
    .btn-label {
      padding: 0.4rem 0.8rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer; margin-top: 0.5rem;
    }
    .create-order-card {
      background: white; border-radius: 8px; padding: 1.5rem;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .create-order-card h3 { margin: 0 0 1rem; }
    .form-row { display: flex; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .form-group { flex: 1; min-width: 150px; }
    .form-group label { display: block; margin-bottom: 0.25rem; font-weight: 500; }
    .form-group input[type="number"],
    .form-group input[type="text"],
    .form-group select {
      width: 100%; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.95rem;
    }
    .create-order-card button {
      padding: 0.6rem 1.2rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer; font-size: 1rem;
    }
    .create-order-card button:disabled { opacity: 0.5; cursor: not-allowed; }
    .error-msg { background: #ffebee; color: #c62828; padding: 0.5rem 1rem; border-radius: 4px; margin-bottom: 1rem; }
    .type-badge {
      padding: 0.25rem 0.75rem; border-radius: 12px; font-size: 0.8rem;
      font-weight: 600; background: #e0e0e0; color: #333;
    }
    .status-actions { display: flex; gap: 0.5rem; margin-top: 0.75rem; align-items: center; }
    .status-actions select {
      flex: 1; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.9rem;
    }
    .status-actions button {
      padding: 0.5rem 1rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .status-actions button:disabled { opacity: 0.5; cursor: not-allowed; }
  `]
})
export class OrdersComponent implements OnInit {
  orders = signal<OrderResponse[]>([]);
  siteOrders = signal<OrderResponse[]>([]);
  isStaff = signal(false);
  rowStatus: Record<number, string> = {};
  loading = signal(false);
  rowVerifyCode: Record<number, string> = {};

  // Order creation fields
  newSiteId: number | null = null;
  newSubtotal: number | null = null;
  newDeliveryZip = '';
  fulfillmentMode: FulfillmentMode = 'DELIVERY';
  sites = signal<Organization[]>([]);
  createError = signal('');

  constructor(private orderService: OrderService, private orgService: OrganizationService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadOrders();
    this.orgService.getByLevel('SITE').subscribe(s => { this.sites.set(s); if (s.length) this.newSiteId = s[0].id; });

    const role = this.authService.userRole();
    const staffRoles = ['STAFF', 'TEAM_LEAD', 'SITE_MANAGER', 'ENTERPRISE_ADMIN'];
    this.isStaff.set(staffRoles.includes(role ?? ''));

    if (this.isStaff() && this.authService.currentUser()?.siteId) {
      this.orderService.getBySite(this.authService.currentUser()!.siteId!).subscribe({
        next: (orders) => this.siteOrders.set(orders),
        error: () => {}
      });
    }
  }

  loadOrders(): void {
    this.loading.set(true);
    this.orderService.getMyOrders().subscribe({
      next: (orders) => {
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  async createOrder(): Promise<void> {
    if (!this.newSiteId || !this.newSubtotal) return;
    this.createError.set('');
    try {
      const result = await this.orderService.createOffline({
        siteId: this.newSiteId,
        subtotal: this.newSubtotal,
        fulfillmentMode: this.fulfillmentMode,
        deliveryZip: this.fulfillmentMode === 'PICKUP' ? undefined : this.newDeliveryZip
      });
      if (result.outcome === 'synced') {
        this.loadOrders();
      }
      this.newSubtotal = null;
      this.newDeliveryZip = '';
    } catch (err: any) {
      this.createError.set(err.error?.error || 'Order creation failed');
    }
  }

  verifyPickup(orderId: number): void {
    const code = this.rowVerifyCode[orderId] || '';
    this.orderService.verifyPickup(orderId, code).subscribe({
      next: (updated) => {
        this.orders.update(orders =>
          orders.map(o => o.id === updated.id ? updated : o)
        );
        this.rowVerifyCode[orderId] = '';
      }
    });
  }

  staffVerifyPickup(orderId: number): void {
    const code = this.rowVerifyCode[orderId] || '';
    this.orderService.verifyPickup(orderId, code).subscribe({
      next: (updated) => {
        this.siteOrders.update(orders => orders.map(o => o.id === updated.id ? updated : o));
        this.rowVerifyCode[orderId] = '';
      },
      error: (err: any) => this.createError.set(err.error?.error || 'Verification failed')
    });
  }

  updateOrderStatus(orderId: number): void {
    const status = this.rowStatus[orderId];
    if (!status) return;
    this.orderService.updateStatus(orderId, status as any).subscribe({
      next: (updated) => {
        this.siteOrders.update(orders => orders.map(o => o.id === updated.id ? updated : o));
        this.rowStatus[orderId] = '';
      },
      error: (err: any) => this.createError.set(err.error?.error || 'Status update failed')
    });
  }

  downloadLabel(orderId: number): void {
    this.orderService.downloadShippingLabel(orderId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `label-order-${orderId}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      }
    });
  }
}
