import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest, User, Role } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = '/api/auth';
  private readonly TOKEN_KEY = 'storehub_token';
  private readonly USER_KEY = 'storehub_user';
  private readonly IDLE_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

  private idleTimer: ReturnType<typeof setTimeout> | null = null;

  currentUser = signal<{ username: string; role: Role; siteId: number | null } | null>(
    this.loadUserFromStorage()
  );

  isAuthenticated = computed(() => this.currentUser() !== null);
  userRole = computed(() => this.currentUser()?.role ?? null);

  constructor(private http: HttpClient, private router: Router) {
    this.setupIdleTracking();
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/login`, request).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/register`, request).pipe(
      tap(res => this.handleAuthSuccess(res))
    );
  }

  reauthenticate(password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/users/reauth', { password }).pipe(
      tap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.token);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
    this.currentUser.set(null);
    this.clearIdleTimer();
    this.router.navigate(['/auth/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private handleAuthSuccess(res: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, res.token);
    const userData = { username: res.username, role: res.role as Role, siteId: res.siteId };
    localStorage.setItem(this.USER_KEY, JSON.stringify(userData));
    this.currentUser.set(userData);
    this.resetIdleTimer();
  }

  private loadUserFromStorage(): { username: string; role: Role; siteId: number | null } | null {
    const stored = localStorage.getItem(this.USER_KEY);
    if (!stored) return null;
    try {
      return JSON.parse(stored);
    } catch {
      return null;
    }
  }

  private setupIdleTracking(): void {
    if (typeof window === 'undefined') return;
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    events.forEach(event => window.addEventListener(event, () => this.resetIdleTimer()));
    this.resetIdleTimer();
  }

  private resetIdleTimer(): void {
    if (!this.isAuthenticated()) return;
    this.clearIdleTimer();
    this.idleTimer = setTimeout(() => this.logout(), this.IDLE_TIMEOUT_MS);
  }

  private clearIdleTimer(): void {
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
  }
}
