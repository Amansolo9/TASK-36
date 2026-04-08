import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OfflineQueueService } from '../../core/services/offline-queue.service';

@Component({
  selector: 'app-offline-status',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (!queue.isOnline()) {
      <div class="offline-banner">Offline — actions will be queued and synced when connection returns</div>
    }
    @if (queue.pendingCount() > 0) {
      <div class="pending-banner">{{ queue.pendingCount() }} action(s) pending sync</div>
    }
  `,
  styles: [`
    .offline-banner { background: #e65100; color: white; text-align: center; padding: 0.4rem; font-size: 0.85rem; }
    .pending-banner { background: #fff3e0; color: #e65100; text-align: center; padding: 0.3rem; font-size: 0.8rem; }
  `]
})
export class OfflineStatusComponent {
  constructor(public queue: OfflineQueueService) {}
}
