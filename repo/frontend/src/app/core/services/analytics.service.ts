import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EventRequest, SiteMetrics, ExperimentDto, BucketResult } from '../models/analytics.model';

@Injectable({ providedIn: 'root' })
export class AnalyticsApiService {
  private readonly API = '/api/analytics';

  constructor(private http: HttpClient) {}

  logEvent(request: EventRequest): Observable<void> {
    return this.http.post<void>(`${this.API}/events`, request);
  }

  getSiteMetrics(siteId: number, start: string, end: string): Observable<SiteMetrics> {
    return this.http.get<SiteMetrics>(`${this.API}/sites/${siteId}/metrics`, {
      params: { start, end }
    });
  }

  createExperiment(exp: ExperimentDto): Observable<ExperimentDto> {
    return this.http.post<ExperimentDto>(`${this.API}/experiments`, exp);
  }

  getActiveExperiments(): Observable<ExperimentDto[]> {
    return this.http.get<ExperimentDto[]>(`${this.API}/experiments`);
  }

  getBucket(experimentName: string): Observable<BucketResult> {
    return this.http.get<BucketResult>(`${this.API}/experiments/${experimentName}/bucket`);
  }

  deactivateExperiment(id: number): Observable<ExperimentDto> {
    return this.http.patch<ExperimentDto>(`${this.API}/experiments/${id}/deactivate`, null);
  }
}
