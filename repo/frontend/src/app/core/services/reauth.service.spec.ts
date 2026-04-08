import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ReauthService } from './reauth.service';
import { AuthService } from './auth.service';
import { of, throwError } from 'rxjs';

describe('ReauthService', () => {
  let service: ReauthService;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', ['reauthenticate', 'getToken']);

    TestBed.configureTestingModule({
      providers: [
        ReauthService,
        { provide: AuthService, useValue: authSpy }
      ]
    });
    service = TestBed.inject(ReauthService);
  });

  describe('isRecentAuthError', () => {
    it('returns true for 403 with RECENT_AUTH_REQUIRED code', () => {
      const error = new HttpErrorResponse({
        status: 403,
        error: { code: 'RECENT_AUTH_REQUIRED', error: 'Recent authentication required.' }
      });
      expect(service.isRecentAuthError(error)).toBeTrue();
    });

    it('returns false for regular 403', () => {
      const error = new HttpErrorResponse({
        status: 403,
        error: { error: 'Access denied' }
      });
      expect(service.isRecentAuthError(error)).toBeFalse();
    });

    it('returns false for 401', () => {
      const error = new HttpErrorResponse({
        status: 401,
        error: { error: 'Unauthorized' }
      });
      expect(service.isRecentAuthError(error)).toBeFalse();
    });
  });

  describe('promptReauth', () => {
    it('sets dialogVisible to true', () => {
      service.promptReauth().subscribe();
      expect(service.dialogVisible()).toBeTrue();
    });

    it('clears previous errors', () => {
      service.dialogError.set('old error');
      service.promptReauth().subscribe();
      expect(service.dialogError()).toBeNull();
    });
  });

  describe('submitPassword', () => {
    it('calls authService.reauthenticate and closes dialog on success', () => {
      authSpy.reauthenticate.and.returnValue(of({ token: 'new', type: 'Bearer', username: 'u', role: 'STAFF', siteId: 1 }));

      let result: { success: boolean } | null = null;
      service.promptReauth().subscribe(r => result = r);

      service.submitPassword('mypassword');

      expect(authSpy.reauthenticate).toHaveBeenCalledWith('mypassword');
      expect(service.dialogVisible()).toBeFalse();
      expect(result).toEqual({ success: true });
    });

    it('shows error on failed re-auth without closing dialog', () => {
      authSpy.reauthenticate.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 401 }))
      );

      service.promptReauth().subscribe();
      service.submitPassword('wrong');

      expect(service.dialogVisible()).toBeTrue();
      expect(service.dialogError()).toContain('Invalid password');
    });
  });

  describe('cancel', () => {
    it('closes dialog and emits failure', () => {
      let result: { success: boolean } | null = null;
      service.promptReauth().subscribe(r => result = r);

      service.cancel();

      expect(service.dialogVisible()).toBeFalse();
      expect(result).toEqual({ success: false });
    });
  });
});
