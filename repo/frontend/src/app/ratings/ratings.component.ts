import { Component, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RatingService } from '../core/services/rating.service';
import { UserService } from '../core/services/user.service';
import { AuthService } from '../core/services/auth.service';
import { RatingResponse } from '../core/models/fulfillment.model';

@Component({
  selector: 'app-ratings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="ratings-page">
      <h2>My Ratings</h2>

      <div class="submit-card">
        <h3>Submit Rating</h3>
        @if (submitError()) { <div class="error-msg">{{ submitError() }}</div> }
        <div class="form-row">
          <div class="form-group"><label>Order ID</label><input type="number" [(ngModel)]="newOrderId"></div>
          <div class="form-group"><label>User ID</label><input type="number" [(ngModel)]="newRatedUserId"></div>
          <div class="form-group">
            <label>Type</label>
            <select [(ngModel)]="newTargetType"><option value="STAFF">Staff</option><option value="CUSTOMER">Customer</option></select>
          </div>
        </div>
        <div class="form-row">
          <div class="form-group"><label>Overall (1-5)</label><input type="number" [(ngModel)]="newStars" min="1" max="5"></div>
          <div class="form-group"><label>Timeliness</label><input type="number" [(ngModel)]="newTimeliness" min="1" max="5"></div>
          <div class="form-group"><label>Communication</label><input type="number" [(ngModel)]="newCommunication" min="1" max="5"></div>
          <div class="form-group"><label>Accuracy</label><input type="number" [(ngModel)]="newAccuracy" min="1" max="5"></div>
        </div>
        <div class="form-group"><label>Comment</label><textarea [(ngModel)]="newComment" rows="2"></textarea></div>
        <button (click)="submitRating()" [disabled]="!newOrderId || !newRatedUserId">Submit Rating</button>
      </div>

      @if (average() !== null) {
        <div class="avg-card">
          <span class="avg-stars">{{ average()! | number:'1.1-1' }}</span>
          <span class="star-icons">
            @for (s of [1,2,3,4,5]; track s) {
              <span [class.filled]="s <= Math.round(average()!)">&#9733;</span>
            }
          </span>
          <span class="avg-label">Average Rating</span>
        </div>
      }

      @for (rating of ratings(); track rating.id) {
        <div class="rating-card">
          <div class="rating-header">
            <span class="stars">
              @for (s of [1,2,3,4,5]; track s) {
                <span [class.filled]="s <= rating.stars">&#9733;</span>
              }
            </span>
            <span class="type-badge">{{ rating.targetType }}</span>
          </div>
          @if (rating.comment) {
            <p class="comment">{{ rating.comment }}</p>
          }
          <div class="rating-meta">
            <small>Order #{{ rating.orderId }} &middot; {{ rating.createdAt | date:'mediumDate' }}</small>
          </div>

          @if (rating.appealStatus) {
            <div class="appeal-status" [class]="'appeal-' + rating.appealStatus.toLowerCase()">
              Appeal: {{ rating.appealStatus }}
            </div>
          } @else {
            <div class="appeal-action">
              <input [(ngModel)]="appealReasons[rating.id]" placeholder="Appeal reason...">
              <button (click)="submitAppeal(rating.id)">Appeal</button>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .ratings-page { max-width: 700px; margin: 2rem auto; padding: 0 1rem; }
    .avg-card {
      background: white; padding: 1.5rem; border-radius: 8px; text-align: center;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .avg-stars { font-size: 2.5rem; font-weight: 700; color: #f9a825; }
    .avg-label { display: block; color: #888; margin-top: 0.25rem; }
    .star-icons span, .stars span { color: #ddd; font-size: 1.3rem; }
    .star-icons span.filled, .stars span.filled { color: #f9a825; }
    .rating-card {
      background: white; padding: 1.25rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 0.75rem;
    }
    .rating-header { display: flex; justify-content: space-between; align-items: center; }
    .type-badge {
      background: #e3f2fd; color: #1565c0; padding: 0.2rem 0.5rem;
      border-radius: 4px; font-size: 0.75rem; font-weight: 600;
    }
    .comment { margin: 0.75rem 0; color: #333; }
    .rating-meta { color: #888; }
    .appeal-status {
      margin-top: 0.5rem; padding: 0.4rem 0.8rem; border-radius: 4px;
      font-size: 0.85rem; font-weight: 500;
    }
    .appeal-pending { background: #fff3e0; color: #e65100; }
    .appeal-in_arbitration { background: #f3e5f5; color: #7b1fa2; }
    .appeal-upheld { background: #e8f5e9; color: #2e7d32; }
    .appeal-overturned { background: #ffebee; color: #c62828; }
    .appeal-action { display: flex; gap: 0.5rem; margin-top: 0.5rem; }
    .appeal-action input { flex: 1; padding: 0.4rem; border: 1px solid #ddd; border-radius: 4px; }
    .appeal-action button {
      padding: 0.4rem 0.8rem; background: #e65100; color: white;
      border: none; border-radius: 4px; cursor: pointer;
    }
    .submit-card {
      background: white; padding: 1.5rem; border-radius: 8px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1); margin-bottom: 1.5rem;
    }
    .submit-card h3 { margin: 0 0 1rem; }
    .form-row { display: flex; gap: 1rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .form-group { flex: 1; min-width: 120px; margin-bottom: 0.5rem; }
    .form-group label { display: block; margin-bottom: 0.25rem; font-weight: 500; }
    .form-group input[type="number"],
    .form-group select,
    .form-group textarea {
      width: 100%; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.95rem;
    }
    .submit-card button {
      padding: 0.6rem 1.2rem; background: #1976d2; color: white;
      border: none; border-radius: 4px; cursor: pointer; font-size: 1rem;
    }
    .submit-card button:disabled { opacity: 0.5; cursor: not-allowed; }
    .error-msg { background: #ffebee; color: #c62828; padding: 0.5rem 1rem; border-radius: 4px; margin-bottom: 1rem; }
  `]
})
export class RatingsComponent implements OnInit {
  Math = Math;
  ratings = signal<RatingResponse[]>([]);
  average = signal<number | null>(null);
  appealReasons: Record<number, string> = {};

  newOrderId: number | null = null;
  newRatedUserId: number | null = null;
  newTargetType: 'STAFF' | 'CUSTOMER' = 'STAFF';
  newStars = 5;
  newTimeliness = 5;
  newCommunication = 5;
  newAccuracy = 5;
  newComment = '';
  submitError = signal('');

  constructor(
    private ratingService: RatingService,
    private userService: UserService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAuthenticated()) return;

    // Get the numeric user ID from /users/me, then load ratings
    this.userService.getMe().subscribe({
      next: (user) => {
        this.ratingService.getForUser(user.id).subscribe({
          next: (ratings) => this.ratings.set(ratings)
        });
        this.ratingService.getAverage(user.id).subscribe({
          next: (result) => this.average.set(result.averageStars)
        });
      }
    });
  }

  submitRating(): void {
    if (!this.newOrderId || !this.newRatedUserId) return;
    this.submitError.set('');
    this.ratingService.submit({
      orderId: this.newOrderId,
      ratedUserId: this.newRatedUserId,
      targetType: this.newTargetType,
      stars: this.newStars,
      timelinessScore: this.newTimeliness,
      communicationScore: this.newCommunication,
      accuracyScore: this.newAccuracy,
      comment: this.newComment
    }).subscribe({
      next: (r) => { this.ratings.update(rs => [r, ...rs]); this.newComment = ''; },
      error: (err: any) => this.submitError.set(err.error?.error || 'Rating failed')
    });
  }

  submitAppeal(ratingId: number): void {
    const reason = this.appealReasons[ratingId];
    if (!reason?.trim()) return;

    this.ratingService.submitAppeal(ratingId, reason).subscribe({
      next: (updated) => {
        this.ratings.update(ratings =>
          ratings.map(r => r.id === updated.id ? updated : r)
        );
      }
    });
  }
}
