export interface EventRequest {
  eventType: EventType;
  siteId?: number;
  target?: string;
  metadata?: string;
}

export type EventType =
  | 'PAGE_VIEW' | 'CLICK' | 'CONVERSION'
  | 'SATISFACTION_RATING' | 'CHECKIN' | 'ORDER_PLACED' | 'TICKET_CREATED';

export interface SiteMetrics {
  siteId: number;
  eventCounts: Record<string, number>;
  uniqueUsers: Record<string, number>;
  funnel: FunnelMetrics;
  satisfaction: { totalRatings: number; uniqueRaters: number; totalActiveUsers: number; satisfactionCoveragePct: number };
  diversity: { distinctEventTypes: number; distinctActiveUsers: number; engagementDiversityIndex: number };
  performanceStatus: 'On Time' | 'Late' | 'Flagged';
}

export interface FunnelMetrics {
  views: number;
  clicks: number;
  conversions: number;
  ctr: number;
  conversionRate: number;
}

export interface ExperimentDto {
  id?: number;
  name: string;
  type: 'AB_TEST' | 'BANDIT';
  variantCount: number;
  description?: string;
  active: boolean;
}

export interface BucketResult {
  experiment: string;
  type: string;
  variant: number;
  variantCount: number;
  active: boolean;
}
