import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface QueuedAction {
  id: string;
  url: string;
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body: any;
  headers?: Record<string, string>;
  status: 'queued' | 'syncing' | 'synced' | 'failed';
  createdAt: number;
  retryCount: number;
  idempotencyKey: string;
  description: string; // human-readable action name
}

const DB_NAME = 'storehub_offline';
const STORE_NAME = 'action_queue';
const DB_VERSION = 1;

@Injectable({ providedIn: 'root' })
export class OfflineQueueService {
  isOnline = signal(navigator.onLine);
  pendingCount = signal(0);
  recentResults = signal<{id: string; status: string; description: string}[]>([]);

  private db: IDBDatabase | null = null;

  constructor(private http: HttpClient) {
    window.addEventListener('online', () => {
      this.isOnline.set(true);
      this.replayQueue();
    });
    window.addEventListener('offline', () => this.isOnline.set(false));
    this.initDb().then(() => this.updatePendingCount());
  }

  private async initDb(): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_NAME)) {
          db.createObjectStore(STORE_NAME, { keyPath: 'id' });
        }
      };
      request.onsuccess = () => { this.db = request.result; resolve(); };
      request.onerror = () => reject(request.error);
    });
  }

  /**
   * Enqueue an action. If online, execute immediately. If offline, queue for later.
   * Returns the action status.
   */
  async enqueue(action: Omit<QueuedAction, 'id' | 'status' | 'createdAt' | 'retryCount'>): Promise<QueuedAction> {
    const queued: QueuedAction = {
      ...action,
      id: crypto.randomUUID(),
      status: 'queued',
      createdAt: Date.now(),
      retryCount: 0
    };

    if (this.isOnline()) {
      // Try immediate execution
      return this.executeAction(queued);
    } else {
      // Store in IndexedDB for later replay
      await this.saveAction(queued);
      this.updatePendingCount();
      this.addResult(queued.id, 'queued', queued.description);
      return queued;
    }
  }

  private async executeAction(action: QueuedAction): Promise<QueuedAction> {
    action.status = 'syncing';
    try {
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'X-Idempotency-Key': action.idempotencyKey,
        ...(action.headers || {})
      };

      // Add auth token
      const token = localStorage.getItem('storehub_token');
      if (token) headers['Authorization'] = `Bearer ${token}`;

      await firstValueFrom(
        this.http.request(action.method, action.url, {
          body: action.body,
          headers
        })
      );
      action.status = 'synced';
      await this.removeAction(action.id);
      this.addResult(action.id, 'synced', action.description);
    } catch (err: any) {
      if (err.status === 0 || !navigator.onLine) {
        // Network error — queue for retry
        action.status = 'queued';
        await this.saveAction(action);
        this.addResult(action.id, 'queued', action.description + ' (will retry)');
      } else {
        action.status = 'failed';
        action.retryCount++;
        if (action.retryCount < 3) {
          await this.saveAction(action);
          this.addResult(action.id, 'failed', action.description + ' (will retry)');
        } else {
          await this.removeAction(action.id);
          this.addResult(action.id, 'failed', action.description + ' (max retries)');
        }
      }
    }
    this.updatePendingCount();
    return action;
  }

  async replayQueue(): Promise<void> {
    const actions = await this.getAllActions();
    for (const action of actions.filter(a => a.status === 'queued' || a.status === 'failed')) {
      await this.executeAction(action);
    }
  }

  // IndexedDB helpers
  private async saveAction(action: QueuedAction): Promise<void> {
    if (!this.db) await this.initDb();
    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).put(action);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  private async removeAction(id: string): Promise<void> {
    if (!this.db) return;
    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(STORE_NAME, 'readwrite');
      tx.objectStore(STORE_NAME).delete(id);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }

  async getAllActions(): Promise<QueuedAction[]> {
    if (!this.db) await this.initDb();
    return new Promise((resolve, reject) => {
      const tx = this.db!.transaction(STORE_NAME, 'readonly');
      const req = tx.objectStore(STORE_NAME).getAll();
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  private async updatePendingCount(): Promise<void> {
    const all = await this.getAllActions();
    this.pendingCount.set(all.filter(a => a.status !== 'synced').length);
  }

  private addResult(id: string, status: string, description: string): void {
    this.recentResults.update(r => [{id, status, description}, ...r.slice(0, 9)]);
  }
}

export interface OfflineActionResult {
  outcome: 'synced' | 'queued' | 'retrying' | 'failed';
  queueId: string;
  description: string;
  /** Only present when outcome is 'synced' — the actual HTTP response is not captured by the queue */
  syncedAt?: number;
}

export function toOfflineResult(action: QueuedAction): OfflineActionResult {
  const statusMap: Record<string, OfflineActionResult['outcome']> = {
    'synced': 'synced', 'queued': 'queued', 'syncing': 'retrying', 'failed': 'failed'
  };
  return {
    outcome: statusMap[action.status] || 'queued',
    queueId: action.id,
    description: action.description,
    syncedAt: action.status === 'synced' ? Date.now() : undefined
  };
}
