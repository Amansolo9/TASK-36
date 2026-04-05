export interface User {
  id: number;
  username: string;
  email: string;
  role: Role;
  siteId: number | null;
  siteName: string | null;
  address: string | null;
  deviceId: string | null;
  enabled: boolean;
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  role: string;
  siteId: number | null;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  siteId?: number;
  role?: string;
}

export interface Organization {
  id: number;
  name: string;
  level: OrgLevel;
  parentId: number | null;
}

export type Role = 'ENTERPRISE_ADMIN' | 'SITE_MANAGER' | 'TEAM_LEAD' | 'STAFF' | 'CUSTOMER';
export type OrgLevel = 'ENTERPRISE' | 'SITE' | 'TEAM';
