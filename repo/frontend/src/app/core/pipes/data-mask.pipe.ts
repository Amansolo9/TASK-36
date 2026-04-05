import { Pipe, PipeTransform, inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

@Pipe({ name: 'dataMask', standalone: true })
export class DataMaskPipe implements PipeTransform {
  private authService = inject(AuthService);

  /** Roles allowed to see unmasked data */
  private readonly UNMASKED_ROLES: Role[] = ['ENTERPRISE_ADMIN', 'SITE_MANAGER'];

  /**
   * Masks sensitive data based on the current user's role.
   * @param value   The raw value to display
   * @param type    'address' | 'deviceId' | 'email' — determines masking pattern
   */
  transform(value: string | null | undefined, type: 'address' | 'deviceId' | 'email' = 'address'): string {
    if (!value) return '—';

    const role = this.authService.userRole();
    if (role && this.UNMASKED_ROLES.includes(role)) {
      return value;
    }

    switch (type) {
      case 'address':
        return this.maskAddress(value);
      case 'deviceId':
        return this.maskDeviceId(value);
      case 'email':
        return this.maskEmail(value);
      default:
        return '****';
    }
  }

  private maskAddress(value: string): string {
    // Show only last 4 characters (e.g. zip)
    if (value.length <= 4) return '****';
    return '*'.repeat(value.length - 4) + value.slice(-4);
  }

  private maskDeviceId(value: string): string {
    if (value.length <= 4) return '****';
    return value.slice(0, 2) + '*'.repeat(value.length - 4) + value.slice(-2);
  }

  private maskEmail(value: string): string {
    const atIndex = value.indexOf('@');
    if (atIndex <= 1) return '****' + value.slice(atIndex);
    return value[0] + '*'.repeat(atIndex - 1) + value.slice(atIndex);
  }
}
