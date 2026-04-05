import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CommunityApiService } from '../core/services/community.service';
import { PointsProfile } from '../core/models/community.model';

@Component({
  selector: 'app-community-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="community-dash">
      @if (profile()) {
        <div class="points-section">
          <div class="badge-card">
            <div class="badge-icon" [class]="'badge-' + profile()!.level.toLowerCase()">
              {{ getBadgeIcon(profile()!.level) }}
            </div>
            <h2>{{ profile()!.badge }}</h2>
            <p class="points-total">{{ profile()!.totalPoints }} points</p>
          </div>

          <!-- Points Meter -->
          <div class="meter-card">
            <h3>Progress Meter</h3>
            <div class="meter-bar">
              <div class="meter-fill" [style.width.%]="meterPercent()"></div>
            </div>
            <div class="meter-labels">
              <span>{{ profile()!.level }}</span>
              @if (profile()!.pointsToNextLevel > 0) {
                <span>{{ profile()!.pointsToNextLevel }} pts to next level</span>
              } @else {
                <span>Max level reached!</span>
              }
            </div>

            <!-- Level thresholds -->
            <div class="thresholds">
              @for (lvl of levels; track lvl.name) {
                <div class="threshold" [class.achieved]="profile()!.totalPoints >= lvl.threshold">
                  <span class="threshold-dot"></span>
                  <span>{{ lvl.name }} ({{ lvl.threshold }})</span>
                </div>
              }
            </div>
          </div>

          <!-- Credit Score Impact Chart -->
          <div class="credit-card">
            <h3>Visible Credit Score Impact</h3>
            <p class="credit-explanation">
              Your community engagement directly affects your trust score.
              Here's how each action contributes:
            </p>
            <div class="impact-chart">
              @for (action of impactActions; track action.name) {
                <div class="impact-row">
                  <span class="impact-name">{{ action.name }}</span>
                  <div class="impact-bar-container">
                    <div class="impact-bar"
                         [class.positive]="action.points > 0"
                         [class.negative]="action.points < 0"
                         [style.width.%]="getBarWidth(action.points)">
                    </div>
                  </div>
                  <span class="impact-value" [class.positive]="action.points > 0"
                        [class.negative]="action.points < 0">
                    {{ action.points > 0 ? '+' : '' }}{{ action.points }}
                  </span>
                </div>
              }
            </div>
          </div>
        </div>
      } @else {
        <p>Loading your community profile...</p>
      }
    </div>
  `,
  styles: [`
    .community-dash { max-width: 700px; margin: 2rem auto; padding: 0 1rem; }
    .badge-card {
      background: white; padding: 2rem; border-radius: 12px; text-align: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .badge-icon {
      font-size: 3rem; width: 80px; height: 80px; margin: 0 auto 1rem;
      border-radius: 50%; display: flex; align-items: center; justify-content: center;
    }
    .badge-newcomer { background: #e0e0e0; }
    .badge-contributor { background: #c8e6c9; }
    .badge-trusted { background: #bbdefb; }
    .badge-champion { background: #fff9c4; }
    .points-total { font-size: 1.5rem; font-weight: 700; color: #1976d2; }
    .meter-card, .credit-card {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .meter-card h3, .credit-card h3 { margin-top: 0; }
    .meter-bar {
      background: #e0e0e0; border-radius: 8px; height: 20px; overflow: hidden; margin: 1rem 0;
    }
    .meter-fill {
      height: 100%; border-radius: 8px; transition: width 0.5s;
      background: linear-gradient(90deg, #66bb6a, #1976d2, #7b1fa2);
    }
    .meter-labels { display: flex; justify-content: space-between; color: #666; font-size: 0.9rem; }
    .thresholds { margin-top: 1rem; }
    .threshold {
      display: flex; align-items: center; gap: 0.5rem; padding: 0.3rem 0;
      color: #999; font-size: 0.85rem;
    }
    .threshold.achieved { color: #2e7d32; font-weight: 600; }
    .threshold-dot {
      width: 10px; height: 10px; border-radius: 50%;
      background: #ddd; display: inline-block;
    }
    .threshold.achieved .threshold-dot { background: #2e7d32; }
    .credit-explanation { color: #666; margin-bottom: 1rem; }
    .impact-chart { display: flex; flex-direction: column; gap: 0.5rem; }
    .impact-row { display: flex; align-items: center; gap: 0.75rem; }
    .impact-name { width: 140px; font-size: 0.9rem; color: #333; }
    .impact-bar-container {
      flex: 1; background: #f5f5f5; border-radius: 4px; height: 16px; overflow: hidden;
    }
    .impact-bar { height: 100%; border-radius: 4px; transition: width 0.3s; }
    .impact-bar.positive { background: #66bb6a; }
    .impact-bar.negative { background: #ef5350; }
    .impact-value { width: 40px; text-align: right; font-weight: 600; font-size: 0.9rem; }
    .impact-value.positive { color: #2e7d32; }
    .impact-value.negative { color: #c62828; }
  `]
})
export class CommunityDashboardComponent implements OnInit {
  profile = signal<PointsProfile | null>(null);

  levels = [
    { name: 'Newcomer', threshold: 0 },
    { name: 'Contributor', threshold: 100 },
    { name: 'Trusted', threshold: 300 },
    { name: 'Champion', threshold: 700 }
  ];

  impactActions = [
    { name: 'Create Post', points: 5 },
    { name: 'Comment', points: 2 },
    { name: 'Receive Upvote', points: 1 },
    { name: 'Receive Downvote', points: -1 },
    { name: 'Post Removed', points: -10 },
    { name: 'Quarantined', points: -20 }
  ];

  constructor(private api: CommunityApiService) {}

  ngOnInit(): void {
    this.api.getMyPoints().subscribe(p => this.profile.set(p));
  }

  meterPercent(): number {
    const p = this.profile();
    if (!p) return 0;
    const maxPoints = 700; // Champion threshold
    return Math.min((p.totalPoints / maxPoints) * 100, 100);
  }

  getBarWidth(points: number): number {
    const max = 20; // scale bar relative to largest impact
    return Math.min((Math.abs(points) / max) * 100, 100);
  }

  getBadgeIcon(level: string): string {
    switch (level) {
      case 'NEWCOMER': return '🌱';
      case 'CONTRIBUTOR': return '⭐';
      case 'TRUSTED': return '🛡️';
      case 'CHAMPION': return '🏆';
      default: return '🌱';
    }
  }
}
