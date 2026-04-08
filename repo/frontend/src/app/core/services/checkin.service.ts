import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, firstValueFrom } from 'rxjs';
import { CheckInRequest, CheckInResponse } from '../models/fulfillment.model';
import { OfflineQueueService, OfflineActionResult, toOfflineResult } from './offline-queue.service';

@Injectable({ providedIn: 'root' })
export class CheckInService {
  private readonly API = '/api/checkins';

  constructor(private http: HttpClient, private offlineQueue: OfflineQueueService) {}

  checkIn(request: CheckInRequest): Observable<CheckInResponse> {
    return this.http.post<CheckInResponse>(this.API, request);
  }

  async checkInOffline(request: CheckInRequest): Promise<{result: OfflineActionResult; response?: CheckInResponse}> {
    if (this.offlineQueue.isOnline()) {
      try {
        const response = await firstValueFrom(this.checkIn(request));
        return {
          result: { outcome: 'synced', queueId: '', description: 'Check-in', syncedAt: Date.now() },
          response
        };
      } catch (err: any) {
        if (err.status === 0 || !navigator.onLine) {
          const action = await this.offlineQueue.enqueue({
            url: '/api/checkins', method: 'POST', body: request,
            idempotencyKey: `checkin-${request.siteId}-${Date.now()}`,
            description: 'Check-in at site ' + request.siteId
          });
          return { result: toOfflineResult(action) };
        }
        throw err;
      }
    } else {
      const action = await this.offlineQueue.enqueue({
        url: '/api/checkins', method: 'POST', body: request,
        idempotencyKey: `checkin-${request.siteId}-${Date.now()}`,
        description: 'Check-in at site ' + request.siteId
      });
      return { result: toOfflineResult(action) };
    }
  }

  getBySite(siteId: number, start: string, end: string): Observable<CheckInResponse[]> {
    return this.http.get<CheckInResponse[]>(`${this.API}/site/${siteId}`, {
      params: { start, end }
    });
  }
}
