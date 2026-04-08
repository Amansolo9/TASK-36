import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TicketRequest, TicketResponse, TicketStatus, EvidenceDto } from '../models/ticket.model';
import { OfflineQueueService, OfflineActionResult, toOfflineResult } from './offline-queue.service';

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly API = '/api/tickets';

  constructor(private http: HttpClient, private offlineQueue: OfflineQueueService) {}

  async createOffline(request: TicketRequest): Promise<OfflineActionResult> {
    const result = await this.offlineQueue.enqueue({
      url: '/api/tickets',
      method: 'POST',
      body: request,
      idempotencyKey: `ticket-create-${Date.now()}-${Math.random().toString(36).slice(2)}`,
      description: 'Create support ticket for order #' + request.orderId
    });
    return toOfflineResult(result);
  }

  create(request: TicketRequest): Observable<TicketResponse> {
    return this.http.post<TicketResponse>(this.API, request);
  }

  getById(id: number): Observable<TicketResponse> {
    return this.http.get<TicketResponse>(`${this.API}/${id}`);
  }

  getMyTickets(): Observable<TicketResponse[]> {
    return this.http.get<TicketResponse[]>(`${this.API}/my`);
  }

  getByStatus(status: TicketStatus): Observable<TicketResponse[]> {
    return this.http.get<TicketResponse[]>(`${this.API}/status/${status}`);
  }

  updateStatus(id: number, status: TicketStatus): Observable<TicketResponse> {
    return this.http.patch<TicketResponse>(`${this.API}/${id}/status`, null, { params: { status } });
  }

  assign(id: number, staffId: number): Observable<TicketResponse> {
    return this.http.patch<TicketResponse>(`${this.API}/${id}/assign`, null, { params: { staffId } });
  }

  uploadEvidence(ticketId: number, file: File): Observable<EvidenceDto> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<EvidenceDto>(`${this.API}/${ticketId}/evidence`, formData);
  }

  getEvidence(ticketId: number): Observable<EvidenceDto[]> {
    return this.http.get<EvidenceDto[]>(`${this.API}/${ticketId}/evidence`);
  }

  verifyIntegrity(evidenceId: number): Observable<{ evidenceId: number; integrityVerified: boolean }> {
    return this.http.get<{ evidenceId: number; integrityVerified: boolean }>(
      `${this.API}/evidence/${evidenceId}/verify`
    );
  }
}
