import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { ReauthService } from '../services/reauth.service';
import { of } from 'rxjs';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authSpy: jasmine.SpyObj<AuthService>;
  let reauthSpy: jasmine.SpyObj<ReauthService>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', ['getToken', 'logout', 'reauthenticate']);
    reauthSpy = jasmine.createSpyObj('ReauthService', ['isRecentAuthError', 'promptReauth']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authSpy },
        { provide: ReauthService, useValue: reauthSpy }
      ]
    });
    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('adds Authorization header when token exists', () => {
    authSpy.getToken.and.returnValue('test-token');
    httpClient.get('/api/test').subscribe();
    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  it('does not add Authorization header when no token', () => {
    authSpy.getToken.and.returnValue(null);
    httpClient.get('/api/test').subscribe();
    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('calls logout on 401', () => {
    authSpy.getToken.and.returnValue('token');
    reauthSpy.isRecentAuthError.and.returnValue(false);
    httpClient.get('/api/test').subscribe({ error: () => {} });
    const req = httpMock.expectOne('/api/test');
    req.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(authSpy.logout).toHaveBeenCalled();
  });

  it('triggers reauth flow on RECENT_AUTH_REQUIRED 403', () => {
    authSpy.getToken.and.returnValue('old-token');
    reauthSpy.isRecentAuthError.and.returnValue(true);
    reauthSpy.promptReauth.and.returnValue(of({ success: true }));

    // After re-auth, interceptor retries with fresh token
    authSpy.getToken.and.returnValue('fresh-token');

    httpClient.patch('/api/orders/1/status', {}).subscribe();

    const originalReq = httpMock.expectOne('/api/orders/1/status');
    originalReq.flush(
      { code: 'RECENT_AUTH_REQUIRED', error: 'Recent authentication required.' },
      { status: 403, statusText: 'Forbidden' }
    );

    expect(reauthSpy.promptReauth).toHaveBeenCalled();

    // Retry request should be made
    const retryReq = httpMock.expectOne('/api/orders/1/status');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    retryReq.flush({ status: 'CONFIRMED' });
  });

  it('propagates error when user cancels reauth', () => {
    authSpy.getToken.and.returnValue('token');
    reauthSpy.isRecentAuthError.and.returnValue(true);
    reauthSpy.promptReauth.and.returnValue(of({ success: false }));

    let errorCaught = false;
    httpClient.patch('/api/orders/1/status', {}).subscribe({
      error: (err: HttpErrorResponse) => {
        errorCaught = true;
        expect(err.status).toBe(403);
      }
    });

    const req = httpMock.expectOne('/api/orders/1/status');
    req.flush(
      { code: 'RECENT_AUTH_REQUIRED' },
      { status: 403, statusText: 'Forbidden' }
    );

    expect(errorCaught).toBeTrue();
  });
});
