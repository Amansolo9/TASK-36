import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CheckInService } from '../core/services/checkin.service';
import { OrganizationService } from '../core/services/organization.service';
import { Organization } from '../core/models/user.model';
import { CheckInResponse } from '../core/models/fulfillment.model';

@Component({
  selector: 'app-checkin',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="checkin-page">
      <h2>One-Tap Check-In</h2>

      <div class="checkin-card">
        <div class="form-group">
          <label for="site">Select Site</label>
          <select id="site" [(ngModel)]="selectedSiteId">
            @for (site of sites(); track site.id) {
              <option [ngValue]="site.id">{{ site.name }}</option>
            }
          </select>
        </div>

        <div class="form-group">
          <label for="location">Location (optional)</label>
          <input id="location" type="text" [(ngModel)]="locationDesc" placeholder="e.g. Main entrance, Building A">
        </div>

        <button class="btn-checkin" (click)="doCheckIn()" [disabled]="loading()">
          {{ loading() ? 'Checking in...' : 'Check In Now' }}
        </button>

        @if (lastResult()) {
          <div class="result" [class]="'result-' + lastResult()!.status.toLowerCase()">
            <strong>{{ lastResult()!.status }}</strong>
            <p>{{ lastResult()!.message }}</p>
            <div class="evidence-summary">
              <small>Time: {{ lastResult()!.timestamp | date:'medium' }}</small>
              <small>Site ID: {{ lastResult()!.siteId }}</small>
              <small>Window: {{ lastResult()!.windowClassification }}</small>
              @if (lastResult()!.deviceEvidenceToken) {
                <small>Device: {{ lastResult()!.deviceEvidenceToken }}</small>
              }
              @if (lastResult()!.locationDescription) {
                <small>Location: {{ lastResult()!.locationDescription }}</small>
              }
              @if (lastResult()!.flaggedForReview) {
                <small class="flagged-badge">Flagged for Review</small>
              }
            </div>
          </div>
        }

        @if (error()) {
          <div class="error">{{ error() }}</div>
        }
      </div>
    </div>
  `,
  styles: [`
    .checkin-page { max-width: 500px; margin: 2rem auto; padding: 0 1rem; }
    .checkin-card {
      background: white; padding: 2rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }
    .form-group { margin-bottom: 1.5rem; }
    label { display: block; margin-bottom: 0.5rem; font-weight: 500; }
    select { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; }
    .btn-checkin {
      width: 100%; padding: 1rem; background: #2e7d32; color: white;
      border: none; border-radius: 8px; font-size: 1.2rem; cursor: pointer;
    }
    .btn-checkin:hover:not(:disabled) { background: #1b5e20; }
    .result { margin-top: 1.5rem; padding: 1rem; border-radius: 8px; }
    .result-valid { background: #e8f5e9; color: #2e7d32; }
    .result-early { background: #fff3e0; color: #e65100; }
    .result-late { background: #fff3e0; color: #e65100; }
    .result-flagged { background: #ffebee; color: #c62828; }
    .error { margin-top: 1rem; color: #c62828; text-align: center; }
    .evidence-summary { margin-top: 0.75rem; display: flex; gap: 1.5rem; flex-wrap: wrap; }
    .evidence-summary small { color: #555; }
    input[type="text"] { width: 100%; padding: 0.75rem; border: 1px solid #ddd; border-radius: 4px; font-size: 1rem; }
  `]
})
export class CheckInComponent implements OnInit {
  sites = signal<Organization[]>([]);
  selectedSiteId: number | null = null;
  locationDesc = '';
  loading = signal(false);
  lastResult = signal<CheckInResponse | null>(null);
  error = signal('');

  constructor(
    private checkInService: CheckInService,
    private orgService: OrganizationService
  ) {}

  ngOnInit(): void {
    this.orgService.getByLevel('SITE').subscribe({
      next: (sites) => {
        this.sites.set(sites);
        if (sites.length > 0) this.selectedSiteId = sites[0].id;
      }
    });
  }

  async doCheckIn(): Promise<void> {
    if (!this.selectedSiteId) return;
    this.loading.set(true);
    this.error.set('');
    this.lastResult.set(null);

    try {
      const { result, response } = await this.checkInService.checkInOffline({
        siteId: this.selectedSiteId,
        scheduledTime: new Date().toISOString(),
        deviceFingerprint: this.getDeviceFingerprint(),
        locationDescription: this.locationDesc || undefined
      });
      if (result.outcome === 'synced' && response) {
        this.lastResult.set(response);
      } else if (result.outcome === 'queued') {
        this.error.set('Check-in queued - will sync when online');
      }
      this.loading.set(false);
    } catch (err: any) {
      this.error.set(err.error?.error || 'Check-in failed');
      this.loading.set(false);
    }
  }

  private getDeviceFingerprint(): string {
    return btoa(navigator.userAgent + screen.width + screen.height);
  }
}
