import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RatingRequest, RatingResponse } from '../models/fulfillment.model';
import { OfflineQueueService, OfflineActionResult, toOfflineResult } from './offline-queue.service';

@Injectable({ providedIn: 'root' })
export class RatingService {
  private readonly API = '/api/ratings';

  constructor(private http: HttpClient, private offlineQueue: OfflineQueueService) {}

  async submitOffline(request: RatingRequest): Promise<OfflineActionResult> {
    const result = await this.offlineQueue.enqueue({
      url: '/api/ratings',
      method: 'POST',
      body: request,
      idempotencyKey: `rating-submit-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      description: 'Submit rating for order #' + request.orderId
    });
    return toOfflineResult(result);
  }

  submit(request: RatingRequest): Observable<RatingResponse> {
    return this.http.post<RatingResponse>(this.API, request);
  }

  getForUser(userId: number): Observable<RatingResponse[]> {
    return this.http.get<RatingResponse[]>(`${this.API}/user/${userId}`);
  }

  getAverage(userId: number): Observable<{ userId: number; averageStars: number }> {
    return this.http.get<{ userId: number; averageStars: number }>(`${this.API}/user/${userId}/average`);
  }

  submitAppeal(ratingId: number, reason: string): Observable<RatingResponse> {
    return this.http.post<RatingResponse>(`${this.API}/${ratingId}/appeal`, null, { params: { reason } });
  }

  resolveAppeal(ratingId: number, resolution: string): Observable<RatingResponse> {
    return this.http.patch<RatingResponse>(`${this.API}/${ratingId}/appeal/resolve`, null, { params: { resolution } });
  }

  getPendingAppeals(): Observable<RatingResponse[]> {
    return this.http.get<RatingResponse[]>(`${this.API}/appeals/pending`);
  }
}
