import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Organization, OrgLevel } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class OrganizationService {
  private readonly API = '/api/organizations';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Organization[]> {
    return this.http.get<Organization[]>(this.API);
  }

  getByLevel(level: OrgLevel): Observable<Organization[]> {
    return this.http.get<Organization[]>(`${this.API}/level/${level}`);
  }

  getChildren(parentId: number): Observable<Organization[]> {
    return this.http.get<Organization[]>(`${this.API}/${parentId}/children`);
  }

  create(org: Partial<Organization>): Observable<Organization> {
    return this.http.post<Organization>(this.API, org);
  }
}
