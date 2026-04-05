import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs';
import { OrderRequest, OrderResponse, OrderStatus } from '../models/fulfillment.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly API = '/api/orders';

  constructor(private http: HttpClient) {}

  create(request: OrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(this.API, request);
  }

  getById(id: number): Observable<OrderResponse> {
    return this.http.get<OrderResponse>(`${this.API}/${id}`);
  }

  getMyOrders(): Observable<OrderResponse[]> {
    return this.http.get<{content: OrderResponse[]}>(`${this.API}/my`).pipe(
      map(page => page.content)
    );
  }

  getBySite(siteId: number): Observable<OrderResponse[]> {
    return this.http.get<{content: OrderResponse[]}>(`${this.API}/site/${siteId}`).pipe(
      map(page => page.content)
    );
  }

  updateStatus(id: number, status: OrderStatus): Observable<OrderResponse> {
    return this.http.patch<OrderResponse>(`${this.API}/${id}/status`, null, { params: { status } });
  }

  verifyPickup(id: number, code: string): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${this.API}/${id}/verify-pickup`, null, { params: { code } });
  }

  downloadShippingLabel(id: number): Observable<Blob> {
    return this.http.get(`${this.API}/${id}/shipping-label`, { responseType: 'blob' });
  }
}
