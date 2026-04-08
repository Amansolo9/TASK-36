export interface CheckInRequest {
  siteId: number;
  scheduledTime?: string; // Optional — server derives actual schedule
  deviceFingerprint?: string;
  locationDescription?: string;
}

export interface CheckInResponse {
  id: number | null;
  userId: number;
  siteId: number;
  timestamp: string;
  scheduledTime: string;
  /** VALID = accepted, FLAGGED = rejected/anomaly */
  status: 'VALID' | 'EARLY' | 'LATE' | 'FLAGGED';
  message: string;
  locationDescription: string | null;
  deviceEvidenceToken: string | null;
  /** Timing classification independent of acceptance status */
  windowClassification: 'EARLY' | 'ON_TIME' | 'LATE' | 'UNKNOWN';
  flaggedForReview: boolean;
}

export type FulfillmentMode = 'PICKUP' | 'DELIVERY' | 'COURIER_HANDOFF';

export interface OrderRequest {
  siteId: number;
  subtotal: number;
  fulfillmentMode: FulfillmentMode;
  deliveryZip?: string;
  courierNotes?: string;
}

export interface OrderResponse {
  id: number;
  customerId: number;
  siteId: number;
  status: OrderStatus;
  fulfillmentMode: FulfillmentMode;
  subtotal: number;
  deliveryFee: number;
  total: number;
  deliveryZip: string | null;
  deliveryDistanceMiles: number | null;
  pickup: boolean;
  pickupVerificationCode: string | null;
  pickupVerified: boolean;
  courierNotes: string | null;
  verifiedById: number | null;
  verifiedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type OrderStatus =
  | 'PENDING' | 'CONFIRMED' | 'PREPARING' | 'READY_FOR_PICKUP'
  | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'PICKED_UP' | 'CANCELLED'
  | 'COURIER_ASSIGNED' | 'COURIER_PICKED_UP' | 'COURIER_DELIVERED';

export interface RatingRequest {
  orderId: number;
  ratedUserId: number;
  targetType: 'STAFF' | 'CUSTOMER';
  stars: number;
  timelinessScore: number;
  communicationScore: number;
  accuracyScore: number;
  comment?: string;
}

export interface RatingResponse {
  id: number;
  orderId: number;
  raterId: number;
  ratedUserId: number;
  targetType: 'STAFF' | 'CUSTOMER';
  stars: number;
  comment: string | null;
  appealStatus: 'PENDING' | 'IN_ARBITRATION' | 'UPHELD' | 'OVERTURNED' | 'EXPIRED' | null;
  appealDeadline: string | null;
  createdAt: string;
}
