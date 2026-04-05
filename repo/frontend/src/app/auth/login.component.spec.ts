import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { LoginComponent } from './login.component';
import { AuthService } from '../core/services/auth.service';
import { AuthResponse } from '../core/models/user.model';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let router: Router;

  const mockAuthResponse: AuthResponse = {
    token: 'fake-jwt-token',
    type: 'Bearer',
    username: 'testuser',
    role: 'STAFF',
    siteId: 1
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['login']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent, RouterTestingModule],
      providers: [
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    router = TestBed.inject(Router);
    spyOn(router, 'navigate');

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should call authService.login on submit', () => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.username = 'testuser';
    component.password = 'password123';
    component.onSubmit();

    expect(authServiceSpy.login).toHaveBeenCalledWith({
      username: 'testuser',
      password: 'password123'
    });
  });

  it('should show error on login failure', fakeAsync(() => {
    const errorResponse = { error: { error: 'Invalid credentials' } };
    authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

    component.username = 'testuser';
    component.password = 'wrongpassword';
    component.onSubmit();
    tick();

    expect(component.error()).toBe('Invalid credentials');
    expect(component.loading()).toBeFalse();
  }));

  it('should navigate to /dashboard on success', fakeAsync(() => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.username = 'testuser';
    component.password = 'password123';
    component.onSubmit();
    tick();

    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  }));
});
