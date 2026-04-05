import { TestBed } from '@angular/core/testing';
import { DataMaskPipe } from './data-mask.pipe';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

describe('DataMaskPipe', () => {
  let pipe: DataMaskPipe;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  function setupWithRole(role: Role | null): void {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['userRole']);
    authServiceSpy.userRole.and.returnValue(role);

    TestBed.configureTestingModule({
      providers: [
        DataMaskPipe,
        { provide: AuthService, useValue: authServiceSpy }
      ]
    });

    pipe = TestBed.inject(DataMaskPipe);
  }

  it('should return unmasked value for ENTERPRISE_ADMIN role', () => {
    setupWithRole('ENTERPRISE_ADMIN');
    expect(pipe.transform('123 Main St', 'address')).toBe('123 Main St');
    expect(pipe.transform('user@example.com', 'email')).toBe('user@example.com');
    expect(pipe.transform('DEVICE-ABC-123', 'deviceId')).toBe('DEVICE-ABC-123');
  });

  it('should return unmasked value for SITE_MANAGER role', () => {
    setupWithRole('SITE_MANAGER');
    expect(pipe.transform('456 Oak Ave', 'address')).toBe('456 Oak Ave');
    expect(pipe.transform('admin@test.com', 'email')).toBe('admin@test.com');
    expect(pipe.transform('DEV-999', 'deviceId')).toBe('DEV-999');
  });

  it('should mask address for STAFF role (shows only last 4 chars)', () => {
    setupWithRole('STAFF');
    const result = pipe.transform('123 Main Street', 'address');
    // '123 Main Street' has 15 chars; last 4 = 'reet'
    expect(result).toBe('***********reet');
  });

  it('should mask email for CUSTOMER role (masks chars before @)', () => {
    setupWithRole('CUSTOMER');
    const result = pipe.transform('user@example.com', 'email');
    // 'user@example.com' -> 'u' + '***' + '@example.com'
    expect(result).toBe('u***@example.com');
  });

  it('should mask deviceId for CUSTOMER role', () => {
    setupWithRole('CUSTOMER');
    const result = pipe.transform('DEVICE-ABC-123', 'deviceId');
    // 'DEVICE-ABC-123' has 14 chars -> first 2 'DE' + 10 asterisks + last 2 '23'
    expect(result).toBe('DE**********23');
  });

  it("should return '\u2014' for null value", () => {
    setupWithRole('STAFF');
    expect(pipe.transform(null, 'address')).toBe('\u2014');
  });

  it("should return '\u2014' for undefined value", () => {
    setupWithRole('STAFF');
    expect(pipe.transform(undefined, 'email')).toBe('\u2014');
  });
});
