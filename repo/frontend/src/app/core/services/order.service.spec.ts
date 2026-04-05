import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { OrderService } from './order.service';
import { OrderRequest, OrderResponse } from '../models/fulfillment.model';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  const mockOrderResponse: OrderResponse = {
    id: 1,
    customerId: 10,
    siteId: 1,
    status: 'PENDING',
    fulfillmentMode: 'DELIVERY',
    subtotal: 25.0,
    deliveryFee: 5.0,
    total: 30.0,
    deliveryZip: '90210',
    deliveryDistanceMiles: 3.5,
    pickup: false,
    pickupVerificationCode: null,
    pickupVerified: false,
    courierNotes: null,
    verifiedById: null,
    verifiedAt: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [OrderService]
    });

    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('create', () => {
    it('should call POST /api/orders with fulfillmentMode', () => {
      const orderRequest: OrderRequest = {
        siteId: 1,
        subtotal: 25.0,
        fulfillmentMode: 'DELIVERY',
        deliveryZip: '90210'
      };

      service.create(orderRequest).subscribe(res => {
        expect(res).toEqual(mockOrderResponse);
      });

      const req = httpMock.expectOne('/api/orders');
      expect(req.request.method).toBe('POST');
      expect(req.request.body.fulfillmentMode).toBe('DELIVERY');
      req.flush(mockOrderResponse);
    });
  });

  describe('getMyOrders', () => {
    it('should unwrap Page response and return content array', () => {
      const mockOrders = [mockOrderResponse];

      service.getMyOrders().subscribe(res => {
        expect(res).toEqual(mockOrders);
        expect(res.length).toBe(1);
      });

      const req = httpMock.expectOne(r => r.url === '/api/orders/my');
      expect(req.request.method).toBe('GET');
      // Backend returns Spring Page wrapper
      req.flush({ content: mockOrders, totalElements: 1, totalPages: 1, number: 0, size: 20 });
    });
  });

  describe('verifyPickup', () => {
    it('should call POST with code param', () => {
      const verifiedOrder: OrderResponse = { ...mockOrderResponse, pickupVerified: true, verifiedById: 5, verifiedAt: '2026-01-02T00:00:00Z' };

      service.verifyPickup(1, 'ABC123').subscribe(res => {
        expect(res.pickupVerified).toBeTrue();
        expect(res.verifiedById).toBe(5);
      });

      const req = httpMock.expectOne(r =>
        r.url === '/api/orders/1/verify-pickup' && r.params.get('code') === 'ABC123'
      );
      expect(req.request.method).toBe('POST');
      req.flush(verifiedOrder);
    });
  });

  describe('updateStatus', () => {
    it('should call PATCH with status param', () => {
      const updatedOrder: OrderResponse = { ...mockOrderResponse, status: 'CONFIRMED' };

      service.updateStatus(1, 'CONFIRMED').subscribe(res => {
        expect(res.status).toBe('CONFIRMED');
      });

      const req = httpMock.expectOne(r =>
        r.url === '/api/orders/1/status' && r.params.get('status') === 'CONFIRMED'
      );
      expect(req.request.method).toBe('PATCH');
      req.flush(updatedOrder);
    });
  });

  describe('downloadShippingLabel', () => {
    it('should call GET and return blob', () => {
      service.downloadShippingLabel(1).subscribe(res => {
        expect(res).toBeTruthy();
        expect(res instanceof Blob).toBeTrue();
      });

      const req = httpMock.expectOne('/api/orders/1/shipping-label');
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['%PDF'], { type: 'application/pdf' }));
    });
  });
});
