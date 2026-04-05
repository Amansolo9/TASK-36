import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { User, Role } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly API = '/api/users';

  constructor(private http: HttpClient) {}

  getAll(): Observable<User[]> {
    return this.http.get<User[]>(this.API);
  }

  getMe(): Observable<User> {
    return this.http.get<User>(`${this.API}/me`);
  }

  getById(id: number): Observable<User> {
    return this.http.get<User>(`${this.API}/${id}`);
  }

  updateRole(id: number, role: Role): Observable<User> {
    return this.http.patch<User>(`${this.API}/${id}/role`, null, { params: { role } });
  }

  disable(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`);
  }
}
