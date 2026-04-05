import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TicketRequest, TicketResponse, TicketStatus, EvidenceDto } from '../models/ticket.model';

@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly API = '/api/tickets';

  constructor(private http: HttpClient) {}

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
