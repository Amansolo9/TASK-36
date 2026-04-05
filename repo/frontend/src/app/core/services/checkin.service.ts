import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CheckInRequest, CheckInResponse } from '../models/fulfillment.model';

@Injectable({ providedIn: 'root' })
export class CheckInService {
  private readonly API = '/api/checkins';

  constructor(private http: HttpClient) {}

  checkIn(request: CheckInRequest): Observable<CheckInResponse> {
    return this.http.post<CheckInResponse>(this.API, request);
  }

  getBySite(siteId: number, start: string, end: string): Observable<CheckInResponse[]> {
    return this.http.get<CheckInResponse[]>(`${this.API}/site/${siteId}`, {
      params: { start, end }
    });
  }
}
