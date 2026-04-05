import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse, Role } from '../models/user.model';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockAuthResponse: AuthResponse = {
    token: 'fake-jwt-token',
    type: 'Bearer',
    username: 'testuser',
    role: 'STAFF',
    siteId: 1
  };

  beforeEach(() => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    localStorage.clear();

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should call POST /api/auth/login and store token', () => {
      const loginRequest = { username: 'testuser', password: 'password123' };

      service.login(loginRequest).subscribe(res => {
        expect(res).toEqual(mockAuthResponse);
        expect(localStorage.getItem('storehub_token')).toBe('fake-jwt-token');
        expect(localStorage.getItem('storehub_user')).toBeTruthy();
      });

      const req = httpMock.expectOne('/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(loginRequest);
      req.flush(mockAuthResponse);
    });
  });

  describe('register', () => {
    it('should call POST /api/auth/register', () => {
      const registerRequest = {
        username: 'newuser',
        email: 'new@example.com',
        password: 'password123'
      };

      service.register(registerRequest).subscribe(res => {
        expect(res).toEqual(mockAuthResponse);
        expect(localStorage.getItem('storehub_token')).toBe('fake-jwt-token');
      });

      const req = httpMock.expectOne('/api/auth/register');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(registerRequest);
      req.flush(mockAuthResponse);
    });
  });

  describe('logout', () => {
    it('should clear localStorage and reset currentUser signal', () => {
      // First log in so there is state to clear
      localStorage.setItem('storehub_token', 'fake-jwt-token');
      localStorage.setItem('storehub_user', JSON.stringify({ username: 'testuser', role: 'STAFF', siteId: 1 }));
      service.currentUser.set({ username: 'testuser', role: 'STAFF' as Role, siteId: 1 });

      service.logout();

      expect(localStorage.getItem('storehub_token')).toBeNull();
      expect(localStorage.getItem('storehub_user')).toBeNull();
      expect(service.currentUser()).toBeNull();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login']);
    });
  });

  describe('getToken', () => {
    it('should return token from localStorage', () => {
      localStorage.setItem('storehub_token', 'my-token');
      expect(service.getToken()).toBe('my-token');
    });

    it('should return null when no token exists', () => {
      expect(service.getToken()).toBeNull();
    });
  });

  describe('isAuthenticated', () => {
    it('should return true when user is set', () => {
      service.currentUser.set({ username: 'testuser', role: 'STAFF' as Role, siteId: 1 });
      expect(service.isAuthenticated()).toBeTrue();
    });

    it('should return false when user is null', () => {
      service.currentUser.set(null);
      expect(service.isAuthenticated()).toBeFalse();
    });
  });
});
