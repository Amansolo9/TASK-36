import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyticsApiService } from '../core/services/analytics.service';
import { OrganizationService } from '../core/services/organization.service';
import { Organization } from '../core/models/user.model';
import { SiteMetrics, ExperimentDto } from '../core/models/analytics.model';

@Component({
  selector: 'app-site-performance',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="analytics-page">
      <h2>Site Performance Dashboard</h2>

      <div class="controls">
        <select [(ngModel)]="selectedSiteId" (ngModelChange)="loadMetrics()">
          @for (site of sites(); track site.id) {
            <option [ngValue]="site.id">{{ site.name }}</option>
          }
        </select>
        <select [(ngModel)]="timeRange" (ngModelChange)="loadMetrics()">
          <option value="7">Last 7 days</option>
          <option value="30">Last 30 days</option>
          <option value="90">Last 90 days</option>
        </select>
      </div>

      @if (metrics()) {
        <!-- Status Chip -->
        <div class="status-row">
          <span class="status-chip" [class]="'chip-' + metrics()!.performanceStatus.toLowerCase().replace(' ', '-')">
            {{ metrics()!.performanceStatus }}
          </span>
          <span class="site-label">Site #{{ metrics()!.siteId }}</span>
        </div>

        <!-- Funnel Metrics -->
        <div class="metrics-grid">
          <div class="metric-card">
            <span class="metric-value">{{ metrics()!.funnel.views | number }}</span>
            <span class="metric-label">Page Views</span>
          </div>
          <div class="metric-card">
            <span class="metric-value">{{ metrics()!.funnel.clicks | number }}</span>
            <span class="metric-label">Clicks</span>
          </div>
          <div class="metric-card">
            <span class="metric-value">{{ metrics()!.funnel.conversions | number }}</span>
            <span class="metric-label">Conversions</span>
          </div>
          <div class="metric-card">
            <span class="metric-value">{{ metrics()!.funnel.ctr | number:'1.1-1' }}%</span>
            <span class="metric-label">CTR</span>
          </div>
          <div class="metric-card">
            <span class="metric-value">{{ metrics()!.funnel.conversionRate | number:'1.1-1' }}%</span>
            <span class="metric-label">Conv. Rate</span>
          </div>
        </div>

        <!-- Funnel Visualization -->
        <div class="funnel-section">
          <h3>Conversion Funnel</h3>
          <div class="funnel-bars">
            <div class="funnel-step">
              <div class="funnel-bar" [style.width.%]="100">
                <span>Views: {{ metrics()!.funnel.views }}</span>
              </div>
            </div>
            <div class="funnel-step">
              <div class="funnel-bar bar-clicks" [style.width.%]="funnelPercent('clicks')">
                <span>Clicks: {{ metrics()!.funnel.clicks }}</span>
              </div>
            </div>
            <div class="funnel-step">
              <div class="funnel-bar bar-conv" [style.width.%]="funnelPercent('conversions')">
                <span>Conv: {{ metrics()!.funnel.conversions }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Diversity Metrics -->
        @if (metrics()!.diversity) {
          <div class="breakdown-section" style="margin-bottom: 1.5rem;">
            <h3>Engagement Diversity</h3>
            <div class="metrics-grid" style="grid-template-columns: repeat(3, 1fr);">
              <div class="metric-card">
                <span class="metric-value">{{ metrics()!.diversity.distinctEventTypes }}</span>
                <span class="metric-label">Event Types</span>
              </div>
              <div class="metric-card">
                <span class="metric-value">{{ metrics()!.diversity.distinctActiveUsers | number }}</span>
                <span class="metric-label">Active Users</span>
              </div>
              <div class="metric-card">
                <span class="metric-value">{{ metrics()!.diversity.engagementDiversityIndex | number:'1.2-2' }}</span>
                <span class="metric-label">Diversity Index</span>
              </div>
            </div>
          </div>
        }

        <!-- Event Breakdown -->
        <div class="breakdown-section">
          <h3>Event Breakdown</h3>
          <table class="data-table">
            <thead>
              <tr><th>Event Type</th><th>Count</th><th>Unique Users</th></tr>
            </thead>
            <tbody>
              @for (key of eventKeys(); track key) {
                <tr>
                  <td>{{ key }}</td>
                  <td>{{ metrics()!.eventCounts[key] | number }}</td>
                  <td>{{ metrics()!.uniqueUsers[key] | number }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Experiments -->
      <div class="experiments-section">
        <h3>Active Experiments</h3>
        @for (exp of experiments(); track exp.id) {
          <div class="experiment-card">
            <div class="exp-header">
              <strong>{{ exp.name }}</strong>
              <span class="exp-type">{{ exp.type }}</span>
              <span class="exp-variants">{{ exp.variantCount }} variants</span>
            </div>
            @if (exp.description) {
              <p>{{ exp.description }}</p>
            }
          </div>
        }
        @if (experiments().length === 0) {
          <p class="empty">No active experiments.</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .analytics-page { max-width: 900px; margin: 2rem auto; padding: 0 1rem; }
    .controls { display: flex; gap: 1rem; margin-bottom: 1.5rem; }
    .controls select { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; }
    .status-row { display: flex; align-items: center; gap: 1rem; margin-bottom: 1.5rem; }
    .status-chip {
      padding: 0.4rem 1rem; border-radius: 20px; font-weight: 700; font-size: 0.9rem;
    }
    .chip-on-time { background: #e8f5e9; color: #2e7d32; }
    .chip-late { background: #fff3e0; color: #e65100; }
    .chip-flagged { background: #ffebee; color: #c62828; }
    .site-label { color: #888; }
    .metrics-grid {
      display: grid; grid-template-columns: repeat(5, 1fr); gap: 1rem; margin-bottom: 1.5rem;
    }
    .metric-card {
      background: white; padding: 1.25rem; border-radius: 8px; text-align: center;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }
    .metric-value { display: block; font-size: 1.5rem; font-weight: 700; color: #1976d2; }
    .metric-label { color: #888; font-size: 0.85rem; }
    .funnel-section, .breakdown-section, .experiments-section {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .funnel-section h3, .breakdown-section h3, .experiments-section h3 { margin-top: 0; }
    .funnel-bars { display: flex; flex-direction: column; gap: 0.5rem; }
    .funnel-bar {
      background: #1976d2; color: white; padding: 0.5rem 1rem;
      border-radius: 4px; min-width: 60px; transition: width 0.5s;
    }
    .bar-clicks { background: #42a5f5; }
    .bar-conv { background: #2e7d32; }
    .data-table { width: 100%; border-collapse: collapse; }
    .data-table th, .data-table td { padding: 0.5rem; text-align: left; border-bottom: 1px solid #eee; }
    .data-table th { background: #fafafa; font-weight: 600; }
    .experiment-card {
      border: 1px solid #eee; padding: 1rem; border-radius: 4px; margin-bottom: 0.5rem;
    }
    .exp-header { display: flex; align-items: center; gap: 0.75rem; }
    .exp-type { background: #e3f2fd; color: #1565c0; padding: 0.1rem 0.5rem; border-radius: 4px; font-size: 0.75rem; }
    .exp-variants { color: #888; font-size: 0.85rem; }
    .empty { color: #888; text-align: center; }
  `]
})
export class SitePerformanceComponent implements OnInit {
  sites = signal<Organization[]>([]);
  metrics = signal<SiteMetrics | null>(null);
  experiments = signal<ExperimentDto[]>([]);
  eventKeys = signal<string[]>([]);

  selectedSiteId: number | null = null;
  timeRange = '30';

  constructor(
    private analyticsApi: AnalyticsApiService,
    private orgService: OrganizationService
  ) {}

  ngOnInit(): void {
    this.orgService.getByLevel('SITE').subscribe(sites => {
      this.sites.set(sites);
      if (sites.length > 0) {
        this.selectedSiteId = sites[0].id;
        this.loadMetrics();
      }
    });
    this.analyticsApi.getActiveExperiments().subscribe(e => this.experiments.set(e));
  }

  loadMetrics(): void {
    if (!this.selectedSiteId) return;
    const end = new Date().toISOString();
    const start = new Date(Date.now() - parseInt(this.timeRange) * 86400000).toISOString();

    this.analyticsApi.getSiteMetrics(this.selectedSiteId, start, end).subscribe(m => {
      this.metrics.set(m);
      this.eventKeys.set(Object.keys(m.eventCounts));
    });
  }

  funnelPercent(stage: 'clicks' | 'conversions'): number {
    const m = this.metrics();
    if (!m || m.funnel.views === 0) return 0;
    const val = stage === 'clicks' ? m.funnel.clicks : m.funnel.conversions;
    return Math.max((val / m.funnel.views) * 100, 5); // min 5% for visibility
  }
}
